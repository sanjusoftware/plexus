package com.bankengine.auth.security;

import com.bankengine.auth.service.AuthorityMappingService;
import com.bankengine.common.model.BankConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final AuthorityMappingService authorityMappingService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        return enrichUser(userRequest, oidcUser);
    }

    /**
     * Enriches the OIDC user with bank-specific authorities and metadata.
     */
    public OidcUser enrichUser(OidcUserRequest userRequest, OidcUser oidcUser) {
        String issuer = oidcUser.getIssuer() != null ? oidcUser.getIssuer().toString() : null;

        // 1. Map authorities and resolve bank config
        AuthorityMappingService.MappingResult mappingResult =
                authorityMappingService.mapAuthoritiesWithContext(oidcUser.getAttributes(), issuer);

        Collection<GrantedAuthority> authorities = mappingResult.authorities();
        BankConfiguration bankConfig = mappingResult.bankConfig();

        // 2. Prepare enriched attributes
        Map<String, Object> claims = new HashMap<>(oidcUser.getAttributes());
        claims.put("bank_id", bankConfig.getBankId());
        claims.put("bankName", bankConfig.getName());
        claims.put("permissions", authorities.stream().map(GrantedAuthority::getAuthority).toList());

        OidcUserInfo enrichedInfo = new OidcUserInfo(claims);

        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        if (userNameAttributeName == null) userNameAttributeName = "sub";

        log.info("[AUTH-SUCCESS] OIDC User '{}' authorized for bank '{}' with {} permissions",
                oidcUser.getName(), bankConfig.getBankId(), authorities.size());

        return new DefaultOidcUser(
                authorities,
                oidcUser.getIdToken(),
                enrichedInfo,
                userNameAttributeName
        );
    }
}