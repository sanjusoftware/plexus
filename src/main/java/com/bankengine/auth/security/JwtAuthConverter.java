package com.bankengine.auth.security;

import com.bankengine.auth.service.PermissionMappingService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    // Define the claim name that holds the user's roles in the JWT.
    // NOTE: This must match the claim name used by your Authorization Server (e.g., "roles").
    private static final String ROLES_CLAIM_NAME = "roles";
    private static final String PRINCIPAL_CLAIM_NAME = "sub"; // Standard user identifier claim ("sub")

    private final PermissionMappingService permissionMappingService;

    // Inject the new service to retrieve permissions from the database/cache.
    public JwtAuthConverter(PermissionMappingService permissionMappingService) {
        this.permissionMappingService = permissionMappingService;
    }

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        return new JwtAuthenticationToken(jwt, authorities, getPrincipalClaimName(jwt));
    }

    private String getPrincipalClaimName(Jwt jwt) {
        return jwt.getClaimAsString(PRINCIPAL_CLAIM_NAME);
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        // 1. Extract Role Names from the JWT (e.g., from the "roles" claim).
        if (jwt.getClaim(ROLES_CLAIM_NAME) == null) {
            return Collections.emptyList();
        }

        // Assuming the roles are passed as a List<String> in the JWT.
        List<String> roleNames = jwt.getClaimAsStringList(ROLES_CLAIM_NAME);

        // 2. Look up the corresponding Permissions using the Caching Service.
        // This service fetches the permissions associated with the extracted roles (from the database/cache).
        Set<String> permissions = permissionMappingService.getPermissionsForRoles(roleNames);

        // 3. Map the permission strings to GrantedAuthority objects.
        // These authorities are then used by Spring Security's @PreAuthorize("hasAuthority('...')").
        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }
}