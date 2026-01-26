package com.bankengine.catalog.controller;

import com.bankengine.catalog.dto.BundleCatalogCard;
import com.bankengine.catalog.dto.ProductCatalogCard;
import com.bankengine.catalog.dto.ProductComparisonView;
import com.bankengine.catalog.dto.ProductDetailView;
import com.bankengine.catalog.service.PublicCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/public/catalog")
@Tag(name = "Public Product Catalog", description = "Customer-facing product discovery APIs")
public class PublicCatalogController {

    private final PublicCatalogService catalogService;

    public PublicCatalogController(PublicCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Operation(summary = "Browse all active products available for subscription")
    @GetMapping("/products")
    public ResponseEntity<Page<ProductCatalogCard>> browseProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long productTypeId,
            @RequestParam(required = false) String customerSegment,
            Pageable pageable) {

        return ResponseEntity.ok(catalogService.getActiveProducts(category, productTypeId, customerSegment, pageable));
    }

    @Operation(summary = "Get detailed product view optimized for customer decision-making")
    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductDetailView> getProductDetails(@PathVariable Long productId) {
        return ResponseEntity.ok(catalogService.getProductDetailView(productId));
    }

    @Operation(summary = "Compare multiple products side-by-side")
    @PostMapping("/products/compare")
    public ResponseEntity<ProductComparisonView> compareProducts(@RequestBody List<Long> productIds) {
        return ResponseEntity.ok(catalogService.compareProducts(productIds));
    }

    @Operation(summary = "Get personalized product recommendations")
    @GetMapping("/products/recommended")
    @PreAuthorize("hasAuthority('catalog:recommend')") // Requires login
    public ResponseEntity<List<ProductCatalogCard>> getRecommendations(
            @RequestParam String customerSegment,
            @RequestParam(required = false) BigDecimal estimatedMonthlyBalance) {

        return ResponseEntity.ok(catalogService.getRecommendedProducts(customerSegment, estimatedMonthlyBalance));
    }

    @GetMapping("/bundles/{id}")
    public ResponseEntity<BundleCatalogCard> getBundleDetails(
            @PathVariable Long id,
            @RequestParam(defaultValue = "RETAIL") String segment) {
        return ResponseEntity.ok(catalogService.getPublicBundleDetails(id, segment));
    }
}
