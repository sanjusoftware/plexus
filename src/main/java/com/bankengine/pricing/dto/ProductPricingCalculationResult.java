package com.bankengine.pricing.dto;

import com.bankengine.pricing.model.PriceValue;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ProductPricingCalculationResult {
    private BigDecimal finalChargeablePrice;
    private List<PriceComponentDetail> componentBreakdown;

    @Data
    @Builder
    public static class PriceComponentDetail {
        private String componentCode;
        private BigDecimal rawValue;
        private PriceValue.ValueType valueType;

        // This field is calculated by the Service, NOT Drools
        private BigDecimal calculatedAmount; // e.g., $50.00 (Result of 5% of $1000)
        private String targetComponentCode; // Optional: for targeted discounts
        private String sourceType;
        private Long matchedTierId;
    }
}