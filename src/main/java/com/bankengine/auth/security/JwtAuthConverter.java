package com.bankengine.auth.security;

import com.bankengine.auth.service.PermissionMappingService;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.repository.BankConfigurationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String ROLES_CLAIM_NAME = "roles";
    private static final String PRINCIPAL_CLAIM_NAME = "sub";
    private static final String BANK_ID_CLAIM_NAME = "bank_id";

    private final PermissionMappingService permissionMappingService;
    private final BankConfigurationRepository bankConfigurationRepository;

    public JwtAuthConverter(PermissionMappingService permissionMappingService,
                            BankConfigurationRepository bankConfigurationRepository) {
        this.permissionMappingService = permissionMappingService;
        this.bankConfigurationRepository = bankConfigurationRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        String bankId = jwt.getClaimAsString(BANK_ID_CLAIM_NAME);
        String issuer = jwt.getIssuer().toString();

        log.info("[AUTH] Token received for Bank: {} | Issuer: {}", bankId, issuer);
        System.out.println("bankId = " + bankId);
        System.out.println("issuer = " + issuer);

        // 1. Extract bank_id
        if (bankId == null || bankId.trim().isEmpty()) {
            log.error("[AUTH-FAIL] Missing bank_id claim");
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "Missing bank_id claim");
        }

        // 2. Verify the combination of Bank ID and Issuer
        BankConfiguration bankConfig;
        try {
            TenantContextHolder.setSystemMode(true);
            System.out.println(">>>>> going to get bank configuration");

            log.debug("[AUTH] Verifying bank configuration in DB for: {}", bankId);
            bankConfig = bankConfigurationRepository.findByBankIdUnfiltered(bankId)
                    .orElseThrow(() -> {
                        log.error("[AUTH-FAIL] Bank configuration missing for: {}", bankId);
                        return new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "Bank " + bankId + " is not onboarded");
                    });

            System.out.println(">>>>> Got to get bank configuration: " + bankConfig);

            String normalizedJwtIss = issuer.replaceAll("/$", "");
            String normalizedDbIss = bankConfig.getIssuerUrl().replaceAll("/$", "");

            if (!normalizedJwtIss.equalsIgnoreCase(normalizedDbIss)) {
                log.error("[AUTH-SECURITY] Issuer mismatch for bank {}. Token: {}, DB: {}", bankId, normalizedJwtIss, normalizedDbIss);
                throw new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "Security Violation: Issuer mismatch");
            }
        } finally {
            TenantContextHolder.setSystemMode(false);
        }

        // Set bank context for the rest of the request thread execution
        TenantContextHolder.setBankId(bankId);
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        log.info("[AUTH-SUCCESS] User authorized with {} permissions", authorities.size());

        return new JwtAuthenticationToken(jwt, authorities, getPrincipalClaimName(jwt));

    }

    private String getPrincipalClaimName(Jwt jwt) {
        return jwt.getClaimAsString(PRINCIPAL_CLAIM_NAME);
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        if (jwt.getClaim(ROLES_CLAIM_NAME) == null) {
            return Collections.emptyList();
        }

        List<String> roleNames = jwt.getClaimAsStringList(ROLES_CLAIM_NAME);
        Set<String> permissions = permissionMappingService.getPermissionsForRoles(roleNames);
        log.info("[AUTH] User '{}' with roles {} has been granted permissions: {}",
                jwt.getClaimAsString("sub"), roleNames, permissions);

        System.out.println(">>> AUTH CHECK: " + roleNames + " -> " + permissions);

        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }
}