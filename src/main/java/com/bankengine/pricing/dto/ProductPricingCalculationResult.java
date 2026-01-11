package com.bankengine.pricing.dto;

import com.bankengine.pricing.model.PriceValue;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
        private BigDecimal amount;
        private PriceValue.ValueType valueType;
        private String sourceType;
        private Long matchedTierId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String createdBy;
        private String updatedBy;
    }
}