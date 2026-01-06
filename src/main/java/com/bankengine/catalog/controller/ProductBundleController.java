package com.bankengine.catalog.controller;

import com.bankengine.catalog.dto.ProductBundleRequest;
import com.bankengine.catalog.service.ProductBundleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bundles")
@RequiredArgsConstructor
public class ProductBundleController {

    private final ProductBundleService bundleService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('catalog:bundle:create')")
    public Long createProductBundle(@Valid @RequestBody ProductBundleRequest dto) {
        return bundleService.createBundle(dto);
    }

    /**
     * Versioned Update: Archives the old bundle and creates a new version from the DTO.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('catalog:bundle:update')")
    public Long updateProductBundle(@PathVariable Long id, @Valid @RequestBody ProductBundleRequest dto) {
        return bundleService.updateBundle(id, dto);
    }

    /**
     * Activation: Validates that constituent products are ACTIVE and flips the bundle status.
     */
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('catalog:bundle:activate')")
    public void activateBundle(@PathVariable Long id) {
        bundleService.activateBundle(id);
    }

    /**
     * Cloning: Deep copies the bundle and its links into a new DRAFT bundle.
     */
    @PostMapping("/{id}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('catalog:bundle:create')")
    public Long cloneBundle(@PathVariable Long id, @RequestParam String newName) {
        return bundleService.cloneBundle(id, newName);
    }

    /**
     * Archive: Soft delete that sets status to ARCHIVED and snaps the expiry date.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('catalog:bundle:delete')")
    public void archiveBundle(@PathVariable Long id) {
        bundleService.archiveBundle(id);
    }
}