package com.bankengine.catalog.controller;

import com.bankengine.catalog.dto.ProductBundleResponse;
import com.bankengine.catalog.dto.ProductResponse;
import com.bankengine.catalog.dto.ProductSearchRequest;
import com.bankengine.catalog.service.ProductBundleService;
import com.bankengine.catalog.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
@Tag(name = "Catalog Display", description = "Public-facing APIs for browsing active products and bundles.")
public class CatalogDisplayController {

    private final ProductService productService;
    private final ProductBundleService bundleService;

    @Operation(summary = "Get all active products", description = "Retrieves a paginated list of all active products for the bank.")
    @GetMapping("/products")
    @PreAuthorize("hasAuthority('catalog:display:read')")
    public ResponseEntity<Page<ProductResponse>> getActiveProducts(ProductSearchRequest criteria) {
        criteria.setStatus("ACTIVE");
        return ResponseEntity.ok(productService.searchProducts(criteria));
    }

    @Operation(summary = "Get product details", description = "Retrieves details of a specific active product.")
    @GetMapping("/products/{id}")
    @PreAuthorize("hasAuthority('catalog:display:read')")
    public ResponseEntity<ProductResponse> getProductDetails(@PathVariable Long id) {
        ProductResponse product = productService.getProductResponseById(id);
        LocalDate now = LocalDate.now();
        if (!"ACTIVE".equals(product.getStatus()) ||
            product.getEffectiveDate().isAfter(now) ||
            (product.getExpirationDate() != null && product.getExpirationDate().isBefore(now))) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(product);
    }

    @Operation(summary = "Get all active bundles", description = "Retrieves a paginated list of all active product bundles for the bank.")
    @GetMapping("/bundles")
    @PreAuthorize("hasAuthority('catalog:display:read')")
    public ResponseEntity<Page<ProductBundleResponse>> getActiveBundles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(bundleService.getActiveBundles(pageable));
    }

    @Operation(summary = "Get bundle details", description = "Retrieves details of a specific active bundle.")
    @GetMapping("/bundles/{id}")
    @PreAuthorize("hasAuthority('catalog:display:read')")
    public ResponseEntity<ProductBundleResponse> getBundleDetails(@PathVariable Long id) {
        ProductBundleResponse bundle = bundleService.getBundleResponseById(id);
        LocalDate now = LocalDate.now();
        if (!"ACTIVE".equals(bundle.getStatus()) ||
            bundle.getActivationDate().isAfter(now) ||
            (bundle.getExpiryDate() != null && bundle.getExpiryDate().isBefore(now))) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(bundle);
    }
}
