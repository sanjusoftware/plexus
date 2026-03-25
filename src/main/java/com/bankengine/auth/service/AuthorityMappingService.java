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
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorityMappingService {

    private static final String ROLES_CLAIM_NAME = "roles";
    private final PermissionMappingService permissionMappingService;
    private final BankConfigurationRepository bankConfigurationRepository;

    public MappingResult mapAuthoritiesWithContext(Map<String, Object> claims, String issuer) {
        if (issuer == null || issuer.isBlank()) {
            throw new OAuth2AuthenticationException("Issuer URL is missing from token.");
        }

        String clientId = extractClientId(claims);
        if (clientId == null) {
            throw new OAuth2AuthenticationException("Client ID is missing from token.");
        }

        BankConfiguration bankConfig = resolveBankConfiguration(issuer, clientId);

        if (bankConfig.getStatus() != BankStatus.ACTIVE) {
            log.warn("[AUTH-DENIED] Login attempt for bank '{}' with status '{}'.", bankConfig.getBankId(), bankConfig.getStatus());
            throw new OAuth2AuthenticationException("Access Denied: This bank is currently " + bankConfig.getStatus().toString().toLowerCase() + ".");
        }

        String realBankId = bankConfig.getBankId();
        TenantContextHolder.setBankId(realBankId);

        try {
            List<String> roleNames = extractRoles(claims);
            Set<String> permissions = permissionMappingService.getPermissionsForRoles(roleNames);

            if (permissions.isEmpty()) {
                log.warn("[AUTH-DENIED] User '{}' has no mapped roles for bank '{}'.", claims.get("sub"), realBankId);
                throw new OAuth2AuthenticationException("Access Denied: Your role is not authorized for this bank.");
            }

            Collection<GrantedAuthority> authorities = permissions.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toSet());

            return new MappingResult(authorities, bankConfig);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private String extractClientId(Map<String, Object> claims) {
        if (claims == null) return null;
        if (claims.get("azp") != null) return claims.get("azp").toString();
        Object aud = claims.get("aud");
        if (aud instanceof String s) return s;
        if (aud instanceof List<?> list && !list.isEmpty()) return list.getFirst().toString();
        return null;
    }

    private BankConfiguration resolveBankConfiguration(String issuer, String clientId) {
        boolean originalSystemMode = TenantContextHolder.isSystemMode();
        try {
            TenantContextHolder.setSystemMode(true);
            String normalizedIssuer = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;

            return bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(normalizedIssuer, clientId)
                    .orElseThrow(() -> new OAuth2AuthenticationException("Bank identity not recognized in system database."));
        } finally {
            TenantContextHolder.setSystemMode(originalSystemMode);
        }
    }

    private List<String> extractRoles(Map<String, Object> claims) {
        Object roles = claims.get(ROLES_CLAIM_NAME);
        if (roles instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
        } else if (roles instanceof String roleStr) {
            return List.of(roleStr);
        }
        return Collections.emptyList();
    }

    /**
     * DTO to pass resolved metadata back to the OIDC service
     */
    public record MappingResult(Collection<GrantedAuthority> authorities, BankConfiguration bankConfig) {}
}