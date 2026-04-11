package com.bankengine.auth.service;

import com.bankengine.auth.model.Role;
import com.bankengine.auth.repository.RoleRepository;
import com.bankengine.common.service.BaseService;
import com.bankengine.web.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleManagementService extends BaseService {

    private final RoleRepository roleRepository;
    private final PermissionMappingService permissionMappingService;

    @Transactional
    public Role saveRoleMapping(String roleName, Set<String> authorities) {
        boolean containsSystemAuth = authorities.stream().anyMatch(a -> a.startsWith("system:"));
        if (containsSystemAuth && !"SYSTEM".equals(getCurrentBankId())) {
            throw new AccessDeniedException("Only SYSTEM bank admins can assign system:* authorities.");
        }

        Role role = roleRepository.findByName(roleName).orElse(new Role());
        role.setName(roleName);
        role.setBankId(getCurrentBankId());
        role.setAuthorities(authorities);

        Role savedRole = roleRepository.save(role);

        // Evict the cache since the role mapping has changed.
        permissionMappingService.evictAllRolePermissionsCache();
        
        return savedRole;
    }

    @Transactional
    public void deleteRole(String roleName, Authentication authentication) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new NotFoundException("Role not found with name: " + roleName));

        if ("SYSTEM_ADMIN".equals(role.getName())) {
            throw new AccessDeniedException("SYSTEM_ADMIN role cannot be deleted.");
        }

        boolean deletingOwnRole = extractCurrentUserRoles(authentication).stream()
                .anyMatch(currentRole -> currentRole.equalsIgnoreCase(role.getName()));
        if (deletingOwnRole) {
            throw new AccessDeniedException("You cannot delete a role assigned to your own account.");
        }

        roleRepository.delete(role);
        permissionMappingService.evictAllRolePermissionsCache();
    }

    private List<String> extractCurrentUserRoles(Authentication authentication) {
        if (authentication == null) {
            return Collections.emptyList();
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return extractRolesFromClaims(jwtAuthenticationToken.getTokenAttributes());
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2User oauth2User) {
            return extractRolesFromClaims(oauth2User.getAttributes());
        }
        if (principal instanceof Jwt jwt) {
            return extractRolesFromClaims(jwt.getClaims());
        }
        if (principal instanceof Map<?, ?> principalMap) {
            Object roles = principalMap.get("roles");
            if (roles instanceof List<?> list) {
                return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
            }
            if (roles instanceof String role) {
                return List.of(role);
            }
        }

        return Collections.emptyList();
    }

    private List<String> extractRolesFromClaims(Map<String, Object> claims) {
        if (claims == null) {
            return Collections.emptyList();
        }

        Object roles = claims.get("roles");
        if (roles instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        if (roles instanceof String role) {
            return List.of(role);
        }

        return Collections.emptyList();
    }

    @Transactional(readOnly = true)
    public Set<String> getAuthoritiesByRoleName(String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new NotFoundException("Role not found with name: " + roleName));

        return role.getAuthorities();
    }

    @Transactional(readOnly = true)
    public List<String> getAllRoleNames() {
        return roleRepository.findAll().stream()
                .map(Role::getName)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Role> getAllRoleMappings() {
        return roleRepository.findAll();
    }
}