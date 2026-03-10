package com.bankengine.catalog.controller;

import com.bankengine.catalog.converter.ProductBundleMapper;
import com.bankengine.catalog.dto.ProductBundleRequest;
import com.bankengine.catalog.dto.ProductBundleResponse;
import com.bankengine.catalog.dto.VersionRequest;
import com.bankengine.catalog.service.ProductBundleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bundles")
@RequiredArgsConstructor
@Tag(name = "Product Bundle Management", description = "Operations for creating and managing lifecycle-versioned product bundles.")
public class ProductBundleController {

    private final ProductBundleService bundleService;
    private final ProductBundleMapper bundleMapper;

    @Operation(summary = "Create a new product bundle aggregate",
            description = "Creates a bundle in DRAFT status. Performs category compatibility checks. Include the 'products' list to initialize links.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Bundle created successfully",
                content = @Content(schema = @Schema(implementation = ProductBundleResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request payload or Main Account constraint violation."),
        @ApiResponse(responseCode = "401", description = "Authentication required."),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions to create bundles."),
        @ApiResponse(responseCode = "422", description = "Business Rule Violation: Incompatible product categories.")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('catalog:bundle:create')")
    public ResponseEntity<ProductBundleResponse> createProductBundle(@Valid @RequestBody ProductBundleRequest dto) {
        return new ResponseEntity<>(bundleService.createBundle(dto), HttpStatus.CREATED);
    }

    @Operation(summary = "Search for a bundle by ID",
            description = "Retrieves full bundle details including constituent products and their configuration (Mandatory vs Optional).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bundle retrieved successfully."),
        @ApiResponse(responseCode = "401", description = "Authentication required."),
        @ApiResponse(responseCode = "404", description = "Bundle not found.")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('catalog:bundle:read')")
    public ResponseEntity<ProductBundleResponse> getBundleById(
            @Parameter(description = "The unique ID of the bundle", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(bundleService.getBundleResponseById(id));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("hasAuthority('catalog:bundle:read')")
    public ResponseEntity<ProductBundleResponse> getBundleByCode(
            @PathVariable String code,
            @RequestParam(required = false) Integer version) {
        return ResponseEntity.ok(bundleMapper.toResponse(
                bundleService.getProductBundleByCode(code, version)));
    }

    @Operation(summary = "Partial update of a DRAFT bundle",
            description = "Consolidated entry point for DRAFT modifications. Synchronizes metadata and product links. Only allowed while status is DRAFT.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bundle updated successfully."),
        @ApiResponse(responseCode = "400", description = "Validation error."),
        @ApiResponse(responseCode = "403", description = "Modification blocked: Bundle is ACTIVE or ARCHIVED."),
        @ApiResponse(responseCode = "404", description = "Bundle not found.")
    })
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('catalog:bundle:update')")
    public ResponseEntity<ProductBundleResponse> patchBundle(
            @Parameter(description = "ID of the DRAFT bundle to update", required = true)
            @PathVariable Long id,
            @RequestBody ProductBundleRequest dto) {
        return ResponseEntity.ok(bundleService.updateBundle(id, dto));
    }

    @Operation(summary = "Version or Branch a bundle",
            description = "Creates a deep copy of an existing bundle into a new DRAFT. Use this to modify ACTIVE bundles without affecting existing contracts.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "New version created successfully.",
                content = @Content(schema = @Schema(implementation = ProductBundleResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid versioning request."),
        @ApiResponse(responseCode = "404", description = "Source bundle not found.")
    })
    @PostMapping("/{id}/create-new-version")
    @PreAuthorize("hasAuthority('catalog:bundle:create')")
    public ResponseEntity<ProductBundleResponse> versionBundle(
            @Parameter(description = "ID of the source bundle to use as a template", required = true)
            @PathVariable Long id,
            @Valid @RequestBody VersionRequest requestDto) {
        return new ResponseEntity<>(bundleService.versionBundle(id, requestDto), HttpStatus.CREATED);
    }

    @Operation(summary = "Activate a bundle",
            description = "Transitions a DRAFT bundle to ACTIVE. Validates that exactly one Main Account exists and all products are ACTIVE.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bundle activated successfully."),
        @ApiResponse(responseCode = "400", description = "Activation failed (e.g., bundle is empty or status is not DRAFT)."),
        @ApiResponse(responseCode = "409", description = "Conflict: One or more products are not in ACTIVE status.")
    })
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('catalog:bundle:activate')")
    public ResponseEntity<ProductBundleResponse> activateBundle(
            @Parameter(description = "ID of the bundle to activate", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(bundleService.activateBundle(id));
    }

    @Operation(summary = "Archive a bundle",
            description = "Soft delete: sets status to ARCHIVED and expiry date to today. Product links are preserved for historical reporting.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Bundle archived successfully."),
        @ApiResponse(responseCode = "403", description = "Unauthorized or invalid state transition.")
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('catalog:bundle:delete')")
    public void archiveBundle(
            @Parameter(description = "ID of the bundle to archive", required = true)
            @PathVariable Long id) {
        bundleService.archiveBundle(id);
    }
}
