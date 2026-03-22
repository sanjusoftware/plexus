package com.bankengine.auth.service;

import com.bankengine.auth.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorityMappingService {

    private static final String ROLES_CLAIM_NAME = "roles";
    private final PermissionMappingService permissionMappingService;

    /**
     * Maps roles from token claims to a collection of GrantedAuthority (permissions).
     * * @param claims The claims from the IDP token.
     * @param bankId The pre-validated Bank ID from the system database.
     * @return A collection of granted authorities.
     */
    public Collection<GrantedAuthority> mapAuthorities(Map<String, Object> claims, String bankId) {
        // 1. Set bank context for the current thread to enable downstream repository filters
        TenantContextHolder.setBankId(bankId);

        // 2. Extract roles from the IDP claims
        List<String> roleNames = extractRoles(claims);

        // 3. Map IDP roles to local database permissions
        Set<String> permissions = permissionMappingService.getPermissionsForRoles(roleNames);

        log.info("[AUTH] User '{}' for bank {} has been granted permissions: {}",
                claims.get("sub"), bankId, permissions);

        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    private List<String> extractRoles(Map<String, Object> claims) {
        Object roles = claims.get(ROLES_CLAIM_NAME);
        if (roles instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return Collections.emptyList();
    }
}