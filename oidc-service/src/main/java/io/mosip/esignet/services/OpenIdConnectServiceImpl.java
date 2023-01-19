/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import io.mosip.esignet.core.dto.AuditDTO;
import io.mosip.esignet.core.dto.IdPTransaction;
import io.mosip.esignet.core.exception.IdPException;
import io.mosip.esignet.core.spi.AuditWrapper;
import io.mosip.esignet.core.spi.OpenIdConnectService;
import io.mosip.esignet.core.spi.TokenService;
import io.mosip.esignet.core.exception.NotAuthenticatedException;
import io.mosip.esignet.core.util.Action;
import io.mosip.esignet.core.util.ActionStatus;
import io.mosip.esignet.core.util.Constants;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class OpenIdConnectServiceImpl implements OpenIdConnectService {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private CacheUtilService cacheUtilService;

    @Autowired
    private AuditWrapper auditWrapper;

    @Value("#{${mosip.esignet.discovery.key-values}}")
    private Map<String, Object> discoveryMap;


    @Override
    public String getUserInfo(String accessToken) throws IdPException {
        String accessTokenHash = null;
        IdPTransaction transaction = null;
        try {
            if(accessToken == null || accessToken.isBlank())
                throw new NotAuthenticatedException();

            String[] tokenParts = IdentityProviderUtil.splitAndTrimValue(accessToken, Constants.SPACE);
            if(tokenParts.length <= 1)
                throw new NotAuthenticatedException();

            if(!Constants.BEARER.equals(tokenParts[0]))
                throw new NotAuthenticatedException();

            accessTokenHash = IdentityProviderUtil.generateOIDCAtHash(tokenParts[1]);
            transaction = cacheUtilService.getUserInfoTransaction(accessTokenHash);
            if(transaction == null)
                throw new NotAuthenticatedException();

            tokenService.verifyAccessToken(transaction.getClientId(), transaction.getPartnerSpecificUserToken(), tokenParts[1]);
            auditWrapper.logAudit(Action.GET_USERINFO, ActionStatus.SUCCESS, new AuditDTO(accessTokenHash,
                    transaction), null);
            return transaction.getEncryptedKyc();

        } catch (IdPException ex) {
            auditWrapper.logAudit(Action.GET_USERINFO, ActionStatus.ERROR, new AuditDTO(accessTokenHash,
                    transaction), null);
            throw ex;
        }
    }

    @Override
    public Map<String, Object> getOpenIdConfiguration() {
        return discoveryMap;
    }
}
