package com.bankengine.auth.security;

import com.bankengine.auth.service.PermissionMappingService;
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
    private static final String BANK_ID_CLAIM_NAME = "bank_id"; // The claim name in your JWT

    private final PermissionMappingService permissionMappingService;

    public JwtAuthConverter(PermissionMappingService permissionMappingService) {
        this.permissionMappingService = permissionMappingService;
    }

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        String bankId = jwt.getClaimAsString(BANK_ID_CLAIM_NAME);

        if (bankId == null || bankId.trim().isEmpty()) {
            log.error("Authentication failed: Missing bank_id claim");
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "Missing bank_id claim in JWT");
        }

        try {
            TenantContextHolder.setBankId(bankId);
            Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
            return new JwtAuthenticationToken(jwt, authorities, getPrincipalClaimName(jwt));

        } finally {
            TenantContextHolder.clear();
        }
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
        log.debug("SECURITY-DEBUG: User '{}' with roles {} has been granted permissions: {}",
                jwt.getClaimAsString("sub"), roleNames, permissions);

        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }
}