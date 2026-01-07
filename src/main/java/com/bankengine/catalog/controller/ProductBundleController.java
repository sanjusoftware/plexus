package com.bankengine.catalog.controller;

import com.bankengine.catalog.dto.ProductBundleRequest;
import com.bankengine.catalog.service.ProductBundleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bundles")
@RequiredArgsConstructor
@Tag(name = "Product Bundle Management", description = "APIs for creating, versioning, and managing life-cycle of product bundles.")
public class ProductBundleController {

    private final ProductBundleService bundleService;

    @Operation(
        summary = "Create a new product bundle",
        description = "Creates a bundle in DRAFT status. Performs cross-product category compatibility checks based on bank configuration."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Bundle created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
        @ApiResponse(responseCode = "422", description = "Business Rule Violation: Incompatible product categories detected")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('catalog:bundle:create')")
    public Long createProductBundle(@Valid @RequestBody ProductBundleRequest dto) {
        return bundleService.createBundle(dto);
    }

    @Operation(
        summary = "Update bundle (Versioning)",
        description = "Archives the existing bundle version and creates a new version with the provided data."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "New version created successfully"),
        @ApiResponse(responseCode = "404", description = "Original bundle not found"),
        @ApiResponse(responseCode = "422", description = "Validation failed for the new version")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('catalog:bundle:update')")
    public Long updateProductBundle(
            @Parameter(description = "ID of the bundle to version") @PathVariable Long id,
            @Valid @RequestBody ProductBundleRequest dto) {
        return bundleService.updateBundle(id, dto);
    }

    @Operation(
        summary = "Activate a bundle",
        description = "Transitions a DRAFT bundle to ACTIVE. Validates that all constituent products are currently ACTIVE."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bundle activated successfully"),
        @ApiResponse(responseCode = "409", description = "Conflict: One or more products are not in ACTIVE status")
    })
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('catalog:bundle:activate')")
    public void activateBundle(@PathVariable Long id) {
        bundleService.activateBundle(id);
    }

    @Operation(
        summary = "Clone an existing bundle",
        description = "Creates a deep copy of an existing bundle (including all product links) in DRAFT status."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Bundle cloned successfully"),
        @ApiResponse(responseCode = "404", description = "Source bundle not found")
    })
    @PostMapping("/{id}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('catalog:bundle:create')")
    public Long cloneBundle(
            @PathVariable Long id,
            @Parameter(description = "Name for the new cloned bundle") @RequestParam String newName) {
        return bundleService.cloneBundle(id, newName);
    }

    @Operation(
        summary = "Archive a bundle",
        description = "Performs a soft delete by setting status to ARCHIVED and setting the expiration date to the current date."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Bundle archived successfully")
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('catalog:bundle:delete')")
    public void archiveBundle(@PathVariable Long id) {
        bundleService.archiveBundle(id);
    }
}