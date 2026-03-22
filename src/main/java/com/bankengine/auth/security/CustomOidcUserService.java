package com.bankengine.auth.security;

import com.bankengine.auth.service.AuthorityMappingService;
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
        String clientId = userRequest.getClientRegistration().getClientId();
        String issuer = oidcUser.getIssuer() != null ? oidcUser.getIssuer().toString() : null;
        log.debug("[AUTH] Handshake successful. Resolving bank for ClientId: {} and Issuer: {}", clientId, issuer);
        var bankConfig = bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(issuer, clientId)
                .orElseThrow(() -> {
                    log.error("[AUTH-ERROR] No bank configuration found for Issuer: {} and ClientId: {}", issuer, clientId);
                    return new OAuth2AuthenticationException("Bank identity not recognized in system database.");
                });

        String realBankId = bankConfig.getBankId();
        Collection<GrantedAuthority> authorities = authorityMappingService.mapAuthorities(
                oidcUser.getAttributes(),
                realBankId
        );

        log.info("[AUTH-SUCCESS] User '{}' authenticated. Source of Truth Bank: {}. Permissions granted: {}",
                oidcUser.getName(), realBankId, authorities.size());

        Map<String, Object> attributes = new HashMap<>(oidcUser.getAttributes());
        attributes.put("bank_id", realBankId);

        return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo()) {
            @Override
            public Map<String, Object> getAttributes() {
                return attributes;
            }
        };
    }
}