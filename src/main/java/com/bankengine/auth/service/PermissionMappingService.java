package com.bankengine.auth.service;

import com.bankengine.auth.model.Role;
import com.bankengine.auth.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service responsible for retrieving and caching the mapping between user roles
 * and their granted authorities (permissions).
 * * This service is called by the JwtAuthConverter during token validation.
 */
@Service
@RequiredArgsConstructor
public class PermissionMappingService {

    private final RoleRepository roleRepository;

    private static final String CACHE_NAME = "rolePermissions";

    /**
     * Retrieves all unique authorities granted to a collection of roles.
     * The results are cached to prevent repeated database lookups for the same set of roles.
     *
     * @param roleNames A collection of role names (e.g., ["ADMIN", "CASHIER"]) extracted from the JWT.
     * @return A Set of unique permission strings (authorities) corresponding to the roles.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_NAME, key = "T(com.bankengine.auth.security.TenantContextHolder).getBankId() + '_' + #roleNames", sync = true)
    public Set<String> getPermissionsForRoles(Collection<String> roleNames) {

        // 1. Fetch all Role entities associated with the given role names.
        // The Role entity's @ElementCollection ensures the authorities are eagerly loaded.
        List<Role> roles = roleRepository.findByNameIn(roleNames);

        if (roles.isEmpty()) {
            return Set.of();
        }

        // 2. Aggregate all authorities from all retrieved roles into a single unique Set.
        // flatMap is used to combine the 'Set<String> authorities' from each Role object.
        return roles.stream()
                .flatMap(role -> role.getAuthorities().stream())
                .collect(Collectors.toSet());
    }

    /**
     * Evicts all entries from the rolePermissions cache.
     * Must be called whenever a role mapping is created, updated, or deleted
     * to ensure the JwtAuthConverter gets fresh data.
     */
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void evictAllRolePermissionsCache() {
        // Method body can be empty. The annotation performs the eviction logic.
    }
}