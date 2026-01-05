package com.bankengine.pricing.dto;

import com.bankengine.pricing.model.PriceValue;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ProductPricingCalculationResult {

    // The final number for the caller to apply
    private BigDecimal finalChargeablePrice;

    // Breakdown for customers and debugging
    private List<PriceComponentDetail> componentBreakdown;

    @Data
    @Builder
    public static class PriceComponentDetail {
        private String componentCode;
        private String context;
        private BigDecimal amount;
        private PriceValue.ValueType valueType;
        private String sourceType; // FIXED_VALUE or RULES_ENGINE
        private Long matchedTierId;
    }
}