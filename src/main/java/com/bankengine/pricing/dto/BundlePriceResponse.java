package com.bankengine.pricing.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class BundlePriceResponse {

    private Long productBundleId;

    private BigDecimal grossTotalAmount; // Total before bundle adjustments
    private BigDecimal netTotalAmount;   // Total after bundle adjustments

    // Itemized results for each product
    private List<ProductPricingResult> productResults;

    // Adjustments applied at the bundle level (e.g., discount, waiver)
    private List<PriceValueResponseDto> bundleAdjustments;

    @Data
    @Builder
    public static class ProductPricingResult {
        private Long productId;
        private BigDecimal productTotalAmount; // Sum of all components for this product
        private List<PriceValueResponseDto> pricingComponents; // Itemized components (fees/rates)
    }
}