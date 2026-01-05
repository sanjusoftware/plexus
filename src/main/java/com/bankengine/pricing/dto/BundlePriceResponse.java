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

    private List<ProductPricingCalculationResult.PriceComponentDetail> bundleAdjustments;
    private List<ProductPricingResult> productResults;

    @Data
    @Builder
    public static class ProductPricingResult {
        private Long productId;
        private BigDecimal productTotalAmount;

        private List<ProductPricingCalculationResult.PriceComponentDetail> pricingComponents;
    }
}