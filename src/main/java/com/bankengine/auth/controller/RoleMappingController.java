package com.bankengine.auth.controller;

import com.bankengine.auth.dto.RoleAuthorityMappingDto;
import com.bankengine.auth.service.AuthorityDiscoveryService;
import com.bankengine.auth.service.RoleManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Role Management", description = "Endpoints for managing roles, authorities, and permissions.")
public class RoleMappingController {

    private final RoleManagementService roleManagementService;
    private final AuthorityDiscoveryService authorityDiscoveryService;

    // --- Role Management Endpoints ---

    @Operation(
            summary = "Create or Update Role-Authority Mapping",
            description = "Creates a new role or updates an existing one, assigning the specified set of authorities. Requires 'auth:role:write' authority.",
            tags = {"Role Management"},
            responses = {
                    @ApiResponse(responseCode = "201", description = "Role mapping created/updated successfully."),
                    @ApiResponse(responseCode = "400", description = "Invalid input or validation failed."),
                    @ApiResponse(responseCode = "403", description = "Forbidden. Missing 'auth:role:write' authority.")
            }
    )
    @PostMapping("/mapping")
    @PreAuthorize("hasAuthority('auth:role:write')")
    public ResponseEntity<Void> mapAuthoritiesToRole(@Valid @RequestBody RoleAuthorityMappingDto dto) {
        roleManagementService.saveRoleMapping(dto.getRoleName(), dto.getAuthorities());
        return ResponseEntity.status(HttpStatus.CREATED).build();
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
    @GetMapping("/system-authorities")
    @PreAuthorize("hasAuthority('auth:role:read')")
    public ResponseEntity<Set<String>> getSystemAuthorities() {
        Set<String> authorities = authorityDiscoveryService.discoverAllAuthorities();
        return ResponseEntity.ok(authorities);
    }
}