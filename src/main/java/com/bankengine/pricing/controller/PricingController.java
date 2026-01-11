package com.bankengine.pricing.controller;

import com.bankengine.pricing.dto.BundlePriceRequest;
import com.bankengine.pricing.dto.BundlePriceResponse;
import com.bankengine.pricing.dto.PricingRequest;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.service.BundlePricingService;
import com.bankengine.pricing.service.PricingCalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Pricing Calculation", description = "Dynamic price retrieval for products and bundles.")
@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingCalculationService pricingCalculationService;
    private final BundlePricingService bundlePricingService;

    @Operation(summary = "Calculate all pricing components for a single product ID",
            description = "Evaluates all fixed and rules-driven components. Returns the final price and breakdown.")
    @ApiResponse(responseCode = "200", description = "Successfully calculated pricing.",
            content = @Content(schema = @Schema(implementation = ProductPricingCalculationResult.class)))
    @PostMapping("/calculate/product")
    @PreAuthorize("hasAuthority('pricing:calculate:read')")
    public ResponseEntity<ProductPricingCalculationResult> calculateProductPrice(
            @Valid @RequestBody PricingRequest request) {

        ProductPricingCalculationResult result = pricingCalculationService.getProductPricing(request);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Calculate the total price for a product bundle")
    @ApiResponse(responseCode = "200", description = "Successfully calculated bundle pricing.")
    @PostMapping("/calculate/bundle")
    @PreAuthorize("hasAuthority('pricing:bundle:calculate:read')")
    public ResponseEntity<BundlePriceResponse> calculateBundlePrice(
            @Valid @RequestBody BundlePriceRequest request) {

        BundlePriceResponse response = bundlePricingService.calculateTotalBundlePrice(request);
        return ResponseEntity.ok(response);
    }
}