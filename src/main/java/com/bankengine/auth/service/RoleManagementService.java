package com.bankengine.auth.service;

import com.bankengine.auth.model.Role;
import com.bankengine.auth.repository.RoleRepository;
import com.bankengine.web.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleManagementService {

    private final RoleRepository roleRepository;
    private final PermissionMappingService permissionMappingService; // Needed for cache eviction

    @Transactional
    public void saveRoleMapping(String roleName, String bankId, Set<String> authorities) {
        Role role = roleRepository.findByName(roleName).orElse(new Role());

        role.setName(roleName);
        role.setBankId(bankId); // Update or set the bank ID
        role.setAuthorities(authorities); // Set the new authorities

        roleRepository.save(role);

        // CRITICAL: Evict the cache since the role mapping has changed.
        permissionMappingService.evictAllRolePermissionsCache();
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
}