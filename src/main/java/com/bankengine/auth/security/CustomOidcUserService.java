package com.bankengine.auth.security;

import com.bankengine.auth.service.AuthorityMappingService;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.BankStatus;
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
        // 1. Fetch standard OIDC user details from IDP
        OidcUser oidcUser = super.loadUser(userRequest);

        String clientId = userRequest.getClientRegistration().getClientId();
        String issuer = oidcUser.getIssuer() != null ? oidcUser.getIssuer().toString() : null;

        log.debug("[AUTH] Handshake successful. Resolving bank for ClientId: {} and Issuer: {}", clientId, issuer);

        // 2. Resolve Bank Configuration (Global/System Context)
        BankConfiguration bankConfig = resolveBankConfiguration(issuer, clientId);
        String realBankId = bankConfig.getBankId();
        String bankDisplayName = bankConfig.getName();

        try {
            // 3. Map authorities using the resolved Bank ID (Tenant Context)
            TenantContextHolder.setBankId(realBankId);
            Collection<GrantedAuthority> authorities = authorityMappingService.mapAuthorities(
                    oidcUser.getAttributes(),
                    realBankId
            );

            // 4. Validation: No permissions = No access
            if (authorities.isEmpty()) {
                log.warn("[AUTH-DENIED] User '{}' has no mapped roles for bank '{}'.", oidcUser.getName(), realBankId);
                throw new OAuth2AuthenticationException("Access Denied: Your role is not authorized for this bank.");
            }

            // 5. Enrich Attributes Map
            // We create a fresh map to include both OIDC claims and our custom bank metadata
            Map<String, Object> enrichedAttributes = new HashMap<>(oidcUser.getAttributes());

            // Add Bank Metadata
            enrichedAttributes.put("bank_id", realBankId);
            enrichedAttributes.put("bankName", bankDisplayName);

            // Add flattened permission list for easier UI consumption
            List<String> permissions = authorities.stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();
            enrichedAttributes.put("permissions", permissions);

            log.info("[AUTH-SUCCESS] User '{}' authenticated. Bank: {}. Permissions granted: {}",
                    oidcUser.getName(), realBankId, permissions.size());

            // 6. Return the enriched OidcUser
            return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo()) {
                @Override
                public Map<String, Object> getAttributes() {
                    return enrichedAttributes;
                }
            };
        } finally {
            // Ensure thread-local is always cleared
            TenantContextHolder.clear();
        }
    }

    /**
     * Finds the bank configuration in the database by matching OIDC issuer and client ID.
     * Uses System Mode to bypass tenant filters during the initial lookup.
     */
    private BankConfiguration resolveBankConfiguration(String issuer, String clientId) {
        try {
            TenantContextHolder.setSystemMode(true);

            BankConfiguration config = bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(issuer, clientId)
                    .orElseThrow(() -> {
                        log.error("[AUTH-ERROR] No bank configuration found for Issuer: {} and ClientId: {}", issuer, clientId);
                        return new OAuth2AuthenticationException("Bank identity not recognized in system database.");
                    });

            if (config.getStatus() != BankStatus.ACTIVE) {
                log.warn("[AUTH-DENIED] Bank '{}' is {}. Access blocked.", config.getBankId(), config.getStatus());
                throw new OAuth2AuthenticationException("Bank account is not active.");
            }

            return config;
        } finally {
            TenantContextHolder.setSystemMode(false);
        }
    }
}