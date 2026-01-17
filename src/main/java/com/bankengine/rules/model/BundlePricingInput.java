package com.bankengine.rules.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@NoArgsConstructor
public class BundlePricingInput {
    private String bankId;
    private String customerSegment;
    private Set<Long> targetPricingComponentIds;
    private List<Long> containedProductIds;
    private BigDecimal grossTotalAmount;

    private Map<String, Object> customAttributes = new HashMap<>();
    private final Map<String, BundleAdjustment> adjustments = new HashMap<>();

    public int getProductCount() {
        return containedProductIds != null ? containedProductIds.size() : 0;
    }

    @Data
    @AllArgsConstructor
    public static class BundleAdjustment {
        private BigDecimal value;
        private String type;
    }

    public void addAdjustment(String code, BigDecimal value, String type) {
        this.adjustments.put(code, new BundleAdjustment(value, type));
    }
}