package com.bankengine.auth.security;

import com.bankengine.auth.service.AuthorityMappingService;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.repository.BankConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
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
    private final BankConfigurationRepository bankConfigurationRepository;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        return enrichUser(userRequest, oidcUser);
    }

    public OidcUser enrichUser(OidcUserRequest userRequest, OidcUser oidcUser) {
        String issuer = oidcUser.getIssuer() != null ? oidcUser.getIssuer().toString() : null;

        // 1. Map authorities
        AuthorityMappingService.MappingResult mappingResult = authorityMappingService.mapAuthoritiesWithContext(oidcUser.getAttributes(), issuer);

        Collection<GrantedAuthority> authorities = mappingResult.authorities();
        BankConfiguration bankConfig = mappingResult.bankConfig();

        Map<String, Object> enrichedAttributes = new HashMap<>(oidcUser.getAttributes());
        enrichedAttributes.put("bank_id", bankConfig.getBankId());
        enrichedAttributes.put("bankName", bankConfig.getName());
        enrichedAttributes.put("permissions", authorities.stream().map(GrantedAuthority::getAuthority).toList());

        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        if (userNameAttributeName == null) userNameAttributeName = "sub";

        return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), userNameAttributeName) {
            @Override
            public Map<String, Object> getAttributes() {
                return enrichedAttributes;
            }
        };
    }
}