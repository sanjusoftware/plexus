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
import java.util.List;
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
        String clientId = userRequest.getClientRegistration().getClientId();

        // 1. Map authorities (This also resolves Bank and checks status)
        Collection<GrantedAuthority> authorities = authorityMappingService.mapAuthorities(oidcUser.getAttributes(), issuer);

        // 2. Resolve metadata (using filtered context if needed, but here we still need name)
        BankConfiguration bankConfig = resolveMetadata(issuer, clientId);

        Map<String, Object> enrichedAttributes = new HashMap<>(oidcUser.getAttributes());
        enrichedAttributes.put("bank_id", bankConfig.getBankId());
        enrichedAttributes.put("bankName", bankConfig.getName());
        enrichedAttributes.put("permissions", authorities.stream().map(GrantedAuthority::getAuthority).toList());

        return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo()) {
            @Override
            public Map<String, Object> getAttributes() {
                return enrichedAttributes;
            }
        };
    }

    private BankConfiguration resolveMetadata(String issuer, String clientId) {
        boolean originalSystemMode = TenantContextHolder.isSystemMode();
        try {
            TenantContextHolder.setSystemMode(true);
            String normalizedIssuer = (issuer != null && issuer.endsWith("/")) ? issuer.substring(0, issuer.length() - 1) : issuer;
            return bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(normalizedIssuer, clientId)
                    .orElseThrow(() -> new OAuth2AuthenticationException("Metadata resolution failed unexpectedly."));
        } finally {
            TenantContextHolder.setSystemMode(originalSystemMode);
        }
    }
}
