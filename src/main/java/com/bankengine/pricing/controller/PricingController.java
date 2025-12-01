package com.bankengine.pricing.controller;

import com.bankengine.pricing.dto.BundlePriceRequest;
import com.bankengine.pricing.dto.BundlePriceResponse;
import com.bankengine.pricing.dto.PriceRequest;
import com.bankengine.pricing.dto.PriceValueResponseDto;
import com.bankengine.pricing.service.BundlePricingService;
import com.bankengine.pricing.service.PricingCalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Pricing Calculation", description = "Dynamic price retrieval for products and bundles across multiple linked components.")
@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingCalculationService pricingCalculationService;
    private final BundlePricingService bundlePricingService;

    // --- 1. SINGLE PRODUCT PRICING ENDPOINT ---

    /**
     * POST /api/v1/pricing/calculate
     * Calculates all linked pricing components for a product based on inputs provided in the JSON body.
     */
    @Operation(summary = "Calculate all pricing components for a single product ID",
            description = "Takes transactional and customer data to evaluate all simple (fixed) and complex (rules-driven) pricing components linked to the given Product ID. Returns a list of identified price results.",
            requestBody = @RequestBody(
                    description = "Input criteria including Product ID, transaction amount, and customer segment.",
                    required = true,
                    content = @Content(schema = @Schema(implementation = PriceRequest.class))
            ))
    @ApiResponse(responseCode = "200", description = "Successfully calculated and returned the list of prices.",
            content = @Content(schema = @Schema(implementation = PriceValueResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "No pricing components were linked to the provided Product ID or the product was not found.")
    @PostMapping("/calculate")
    @PreAuthorize("hasAuthority('pricing:calculate:read')")
    public ResponseEntity<List<PriceValueResponseDto>> calculateProductPrice(
            @Valid @RequestBody PriceRequest request) {

        List<PriceValueResponseDto> calculatedPrices = pricingCalculationService.getProductPricing(request);

        return ResponseEntity.ok(calculatedPrices);
    }

    // --- 2. BUNDLE PRODUCT PRICING ENDPOINT ---

    /**
     * POST /api/v1/pricing/bundle/calculate
     * Calculates the total price for a bundle of products, including itemized costs and bundle discounts.
     */
    @Operation(summary = "Calculate the total price for a product bundle",
            description = "Takes a list of product IDs and context (customer segment) to calculate the total aggregated price, itemized fees, and apply multi-tenant, rules-driven bundle adjustments.",
            requestBody = @RequestBody(
                    description = "Bundle criteria including Bundle ID, list of products, and customer segment.",
                    required = true,
                    content = @Content(schema = @Schema(implementation = BundlePriceRequest.class))
            ))
    @ApiResponse(responseCode = "200", description = "Successfully calculated and returned the comprehensive bundle pricing breakdown.",
            content = @Content(schema = @Schema(implementation = BundlePriceResponse.class)))
    @ApiResponse(responseCode = "404", description = "The bundle or its required components were not found.")
    @PostMapping("/bundle/calculate") // NEW ENDPOINT PATH
    @PreAuthorize("hasAuthority('pricing:bundle:calculate:read')")
    public ResponseEntity<BundlePriceResponse> calculateBundlePrice(
            @Valid @RequestBody BundlePriceRequest request) {

        BundlePriceResponse response = bundlePricingService.calculateTotalBundlePrice(request);

        return ResponseEntity.ok(response);
    }
}