package com.bankengine.auth.service;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.BankStatus;
import com.bankengine.common.repository.BankConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorityMappingService {

    private static final String ROLES_CLAIM_NAME = "roles";
    private static final String AUD_CLAIM_NAME = "aud";
    private static final String AZP_CLAIM_NAME = "azp";

    private final PermissionMappingService permissionMappingService;
    private final BankConfigurationRepository bankConfigurationRepository;

    /**
     * Maps roles from token claims to a collection of GrantedAuthority (permissions).
     * Also validates that the bank is onboarded, active, and the issuer matches.
     *
     * @param claims The claims from the JWT or OIDC token.
     * @param issuer The issuer URL from the token.
     * @return A collection of granted authorities.
     */
    public Collection<GrantedAuthority> mapAuthorities(Map<String, Object> claims, String issuer) {
        String clientId = extractClientId(claims);
        String bankId = resolveBankId(issuer, clientId);

        // Set bank context for the rest of the request thread execution
        TenantContextHolder.setBankId(bankId);

        List<String> roleNames = extractRoles(claims);
        Set<String> permissions = permissionMappingService.getPermissionsForRoles(roleNames);

        log.info("[AUTH] User '{}' with roles {} for bank {} has been granted permissions: {}",
                claims.get("sub"), roleNames, bankId, permissions);

        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    public String resolveBankId(String issuer, String clientId) {
        if (issuer == null) {
            log.error("[AUTH-FAIL] Missing issuer");
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "Missing issuer");
        }
        if (clientId == null) {
            log.error("[AUTH-FAIL] Missing client_id (aud/azp)");
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "Missing client identification");
        }

        try {
            TenantContextHolder.setSystemMode(true);
            String normalizedIssuer = issuer.replaceAll("/$", "");
            log.debug("[AUTH] Resolving bank for issuer: {} and clientId: {}", normalizedIssuer, clientId);

            BankConfiguration bankConfig = bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(normalizedIssuer, clientId)
                    .orElseThrow(() -> {
                        log.error("[AUTH-FAIL] No bank configuration found for issuer: {} and clientId: {}", normalizedIssuer, clientId);
                        return new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "Bank not onboarded for this provider");
                    });

            if (bankConfig.getStatus() != BankStatus.ACTIVE) {
                log.warn("[AUTH-FAIL] Bank {} is not ACTIVE. Current status: {}", bankConfig.getBankId(), bankConfig.getStatus());
                throw new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "Bank account is not active. Please contact support.");
            }

            return bankConfig.getBankId();
        } finally {
            TenantContextHolder.setSystemMode(false);
        }
    }

    private String extractClientId(Map<String, Object> claims) {
        // Check 'azp' (Authorized Party) first - common in OIDC for the client ID
        Object azp = claims.get(AZP_CLAIM_NAME);
        if (azp instanceof String s && !s.isBlank()) {
            return s;
        }

        // Check 'aud' (Audience) - can be a string or a list of strings
        Object aud = claims.get(AUD_CLAIM_NAME);
        if (aud instanceof String s && !s.isBlank()) {
            return s;
        } else if (aud instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    private List<String> extractRoles(Map<String, Object> claims) {
        Object roles = claims.get(ROLES_CLAIM_NAME);
        if (roles instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return Collections.emptyList();
    }
}
