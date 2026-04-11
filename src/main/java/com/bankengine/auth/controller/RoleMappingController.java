package com.bankengine.auth.controller;

import com.bankengine.auth.dto.RoleAuthorityMappingDto;
import com.bankengine.auth.model.Role;
import com.bankengine.auth.service.AuthorityDiscoveryService;
import com.bankengine.auth.service.RoleManagementService;
import com.bankengine.auth.service.SessionAuthorityRefreshService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;


@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Role Management", description = "Endpoints for managing roles, authorities, and permissions.")
public class RoleMappingController {

    private final RoleManagementService roleManagementService;
    private final AuthorityDiscoveryService authorityDiscoveryService;
    private final SessionAuthorityRefreshService sessionAuthorityRefreshService;

    // --- Role Management Endpoints ---

    @Operation(
            summary = "Create or Update Role-Authority Mapping",
            description = "Creates a new role or updates an existing one, assigning the specified set of authorities. Requires 'auth:role:write' authority.",
            tags = {"Role Management"},
            responses = {
                    @ApiResponse(responseCode = "201", description = "Role mapping created/updated successfully.",
                            content = @Content(schema = @Schema(implementation = Role.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input or validation failed."),
                    @ApiResponse(responseCode = "403", description = "Forbidden. Missing 'auth:role:write' authority.")
            }
    )
    @PostMapping("/mapping")
    @PreAuthorize("hasAuthority('auth:role:write')")
    public ResponseEntity<Role> mapAuthoritiesToRole(@Valid @RequestBody RoleAuthorityMappingDto dto,
                                                     Authentication authentication,
                                                     HttpServletRequest request) {
        Role savedRole = roleManagementService.saveRoleMapping(dto.getRoleName(), dto.getAuthorities());
        // Keep current session in sync when the caller updates one of their own roles.
        sessionAuthorityRefreshService.refreshCurrentSessionAuthorities(authentication, dto.getRoleName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedRole);
    }

    @Operation(
            summary = "Delete Role Mapping",
            description = "Deletes the specified role and all its associated authority mappings. Requires 'auth:role:write' authority.",
            tags = {"Role Management"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Role mapping deleted successfully."),
                    @ApiResponse(responseCode = "404", description = "Role not found."),
                    @ApiResponse(responseCode = "403", description = "Forbidden. Missing 'auth:role:write' authority or attempting to delete SYSTEM_ADMIN.")
            }
    )
    @DeleteMapping("/{roleName}")
    @PreAuthorize("hasAuthority('auth:role:write')")
    public ResponseEntity<Void> deleteRoleMapping(
            @Parameter(description = "The unique name of the role to delete")
            @PathVariable String roleName,
            Authentication authentication,
            HttpServletRequest request
    ) {
        roleManagementService.deleteRole(roleName);
        // Keep current session in sync when the caller deletes one of their own roles.
        sessionAuthorityRefreshService.refreshCurrentSessionAuthorities(authentication, roleName, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Retrieve Authorities by Role Name",
            description = "Fetches the complete set of authorities (permissions) currently mapped to the given role. Requires 'auth:role:read' authority.",
            tags = {"Role Management"},
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Successfully retrieved authorities.",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = String.class)) // The schema is Set<String>
                    ),
                    @ApiResponse(responseCode = "404", description = "Role not found."),
                    @ApiResponse(responseCode = "403", description = "Forbidden. Missing 'auth:role:read' authority.")
            }
    )
    @GetMapping("/{roleName}")
    @PreAuthorize("hasAuthority('auth:role:read')")
    public ResponseEntity<Set<String>> getRoleAuthorities(
            @Parameter(description = "The unique name of the role (e.g., 'DEV_ADMIN')")
            @PathVariable String roleName
    ) {
        Set<String> authorities = roleManagementService.getAuthoritiesByRoleName(roleName);
        return ResponseEntity.ok(authorities);
    }

    @Operation(
            summary = "List All Defined Roles",
            description = "Retrieves a list of all unique role names currently defined in the system. Requires 'auth:role:read' authority.",
            tags = {"Role Management"},
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Successfully retrieved the list of role names.",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = String.class)) // The schema is List<String>
                    ),
                    @ApiResponse(responseCode = "403", description = "Forbidden. Missing 'auth:role:read' authority.")
            }
    )
    @GetMapping
    @PreAuthorize("hasAuthority('auth:role:read')")
    public ResponseEntity<List<String>> getAllRoleNames() {
        List<String> roleNames = roleManagementService.getAllRoleNames();
        return ResponseEntity.ok(roleNames);
    }

    @GetMapping("/mapping")
    @PreAuthorize("hasAuthority('auth:role:read')")
    public ResponseEntity<List<com.bankengine.auth.model.Role>> getAllRoleMappings() {
        return ResponseEntity.ok(roleManagementService.getAllRoleMappings());
    }

    // --- UI Utility Endpoints ---

    @Operation(
            summary = "Discover All System Authorities",
            description = "Utility endpoint to discover all unique authorities (permissions) used across the entire application by scanning code. Used for Admin UI configuration. Requires 'auth:role:read' authority.",
            tags = {"UI Utility"},
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Successfully retrieved all discoverable authorities.",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = String.class))
                    ),
                    @ApiResponse(responseCode = "403", description = "Forbidden. Missing 'auth:role:read' authority."),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing token")
            }
    )
    @GetMapping("/authorities")
    @PreAuthorize("hasAuthority('auth:role:read')")
    public ResponseEntity<Set<String>> getSystemAuthorities() {
        Set<String> authorities = authorityDiscoveryService.discoverAllAuthorities();
        return ResponseEntity.ok(authorities);
    }

    @Operation(
            summary = "Get Endpoint to Permission Mapping",
            description = "Retrieves a mapping of API endpoints to their required permissions. Used by the frontend for dynamic RBAC.",
            tags = {"UI Utility"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully retrieved permissions map."),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing token")
            }
    )
    @GetMapping("/permissions-map")
    public ResponseEntity<Map<String, Set<String>>> getPermissionsMap() {
        return ResponseEntity.ok(authorityDiscoveryService.discoverEndpointPermissions());
    }
}