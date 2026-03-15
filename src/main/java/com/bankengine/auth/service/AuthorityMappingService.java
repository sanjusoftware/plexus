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
    private static final String BANK_ID_CLAIM_NAME = "bank_id";

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
        String bankId = (String) claims.get(BANK_ID_CLAIM_NAME);
        if (bankId == null || bankId.trim().isEmpty()) {
            log.error("[AUTH-FAIL] Missing bank_id claim/attribute");
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "Missing bank_id");
        }

        validateBankAndIssuer(bankId, issuer);

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

    public void validateBankAndIssuer(String bankId, String issuer) {
        if (issuer == null) {
            log.error("[AUTH-FAIL] Missing issuer for bank: {}", bankId);
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "Missing issuer");
        }

        BankConfiguration bankConfig;
        try {
            TenantContextHolder.setSystemMode(true);
            log.debug("[AUTH] Verifying bank configuration in DB for: {}", bankId);
            bankConfig = bankConfigurationRepository.findByBankIdUnfiltered(bankId)
                    .orElseThrow(() -> {
                        log.error("[AUTH-FAIL] Bank configuration missing for: {}", bankId);
                        return new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "Bank " + bankId + " is not onboarded");
                    });

            String normalizedJwtIss = issuer.replaceAll("/$", "");
            String normalizedDbIss = bankConfig.getIssuerUrl().replaceAll("/$", "");

            if (!normalizedJwtIss.equalsIgnoreCase(normalizedDbIss)) {
                log.error("[AUTH-SECURITY] Issuer mismatch for bank {}. Provided: {}, DB: {}", bankId, normalizedJwtIss, normalizedDbIss);
                throw new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "Security Violation: Issuer mismatch");
            }

            if (bankConfig.getStatus() != BankStatus.ACTIVE) {
                log.warn("[AUTH-FAIL] Bank {} is not ACTIVE. Current status: {}", bankId, bankConfig.getStatus());
                throw new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "Bank account is not active. Please contact support.");
            }
        } finally {
            TenantContextHolder.setSystemMode(false);
        }
    }

    private List<String> extractRoles(Map<String, Object> claims) {
        Object roles = claims.get(ROLES_CLAIM_NAME);
        if (roles instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return Collections.emptyList();
    }
}
