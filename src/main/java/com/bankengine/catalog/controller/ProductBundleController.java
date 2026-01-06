package com.bankengine.catalog.controller;

import com.bankengine.catalog.dto.BundleProductLinkRequest;
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

    @PostMapping("/{bundleId}/links")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('catalog:bundle:update')")
    public void linkProductToBundle(
            @PathVariable Long bundleId,
            @Valid @RequestBody BundleProductLinkRequest dto) {

        bundleService.linkProduct(
                bundleId,
                dto.getProductId(),
                dto.isMainAccount(),
                dto.isMandatory()
        );
    }
}