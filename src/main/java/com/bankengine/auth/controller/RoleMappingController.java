package com.bankengine.auth.controller;

import com.bankengine.auth.dto.RoleAuthorityMappingDto;
import com.bankengine.auth.service.AuthorityDiscoveryService;
import com.bankengine.auth.service.RoleManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleMappingController {

    private final RoleManagementService roleManagementService;
    private final AuthorityDiscoveryService authorityDiscoveryService;

    // --- Role Management Endpoints ---

    /**
     * Creates or updates a Role and assigns the given set of authorities to it.
     * Requires ADMIN privileges.
     */
    @PostMapping("/mapping")
    @PreAuthorize("hasAuthority('auth:role:write')")
    public ResponseEntity<Void> mapAuthoritiesToRole(@Valid @RequestBody RoleAuthorityMappingDto dto) {
        roleManagementService.saveRoleMapping(dto.getRoleName(), dto.getBankId(), dto.getAuthorities());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Retrieves the authorities mapped to a specific role.
     * Requires ADMIN privileges.
     */
    @GetMapping("/{roleName}")
    @PreAuthorize("hasAuthority('auth:role:read')")
    public ResponseEntity<Set<String>> getRoleAuthorities(@PathVariable String roleName) {
        Set<String> authorities = roleManagementService.getAuthoritiesByRoleName(roleName);
        return ResponseEntity.ok(authorities);
    }

    /**
     * Lists all defined roles in the system.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('auth:role:read')")
    public ResponseEntity<List<String>> getAllRoleNames() {
        List<String> roleNames = roleManagementService.getAllRoleNames();
        return ResponseEntity.ok(roleNames);
    }

    // --- UI Utility Endpoints ---

    /**
     * Fetches all unique authorities used across ALL controllers in the application
     * via reflection. This list is used by the Admin UI to build the permission assignment matrix.
     */
    @GetMapping("/system-authorities")
    @PreAuthorize("hasAuthority('auth:role:read')") // Can be granted to a lower-level reader
    public ResponseEntity<Set<String>> getSystemAuthorities() {
        Set<String> authorities = authorityDiscoveryService.discoverAllAuthorities();
        return ResponseEntity.ok(authorities);
    }
}