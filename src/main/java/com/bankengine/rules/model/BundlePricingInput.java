package com.bankengine.rules.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@NoArgsConstructor
public class BundlePricingInput {
    private String bankId;
    private String customerSegment;
    private LocalDate referenceDate = LocalDate.now();

    private Set<Long> targetPricingComponentIds;
    private Set<Long> activePricingTierIds;
    private List<Long> containedProductIds;
    private BigDecimal grossTotalAmount;

    private Map<String, Object> customAttributes = new HashMap<>();
    private final Map<String, BundleAdjustment> adjustments = new HashMap<>();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class BundleAdjustment {
        private BigDecimal value;
        private String type;
        private String targetComponentCode;
    }

    /**
     * Standard adjustment applying to the broad base (usually total fees).
     */
    public void addAdjustment(String code, BigDecimal value, String type) {
        this.addAdjustment(code, value, type, null);
    }

    /**
     * Surgical adjustment targeting a specific component code.
     */
    public void addAdjustment(String code, BigDecimal value, String type, String targetComponentCode) {
        this.adjustments.put(code, new BundleAdjustment(value, type, targetComponentCode));
    }
}