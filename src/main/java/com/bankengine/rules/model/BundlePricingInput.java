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

    private Set<String> targetPricingComponentCodes;
    private Set<String> activePricingTierCodes;
    private List<Long> containedProductIds;
    private BigDecimal grossTotalAmount;

    private Map<String, Object> customAttributes = new HashMap<>();
    private Map<String, BundleAdjustment> adjustments = new HashMap<>();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class BundleAdjustment {
        private BigDecimal value;
        private String type;
        private String targetComponentCode;
        private boolean applyChargeOnFullBreach;
        private String matchedTierCode;
    }

    /**
     * Standard adjustment applying to the broad base (usually total fees).
     */
    public void addAdjustment(String code, BigDecimal value, String type) {
        this.addAdjustment(code, value, type, null, false);
    }

    /**
     * Overloaded method to support Drools BundleRuleBuilderService (4 arguments).
     * Maps to: $input.addAdjustment(code, value, type, tierCode)
     */
    public void addAdjustment(String code, BigDecimal value, String type, String tierCode) {
        this.addAdjustment(code, value, type, null, false, tierCode);
    }

    /**
     * Surgical adjustment targeting a specific component code.
     */
    public void addAdjustment(String code, BigDecimal value, String type, String targetComponentCode, boolean isFullBreach) {
        this.addAdjustment(code, value, type, targetComponentCode, isFullBreach, null);
    }

    /**
     * Internal master method to build the adjustment with all metadata.
     */
    private void addAdjustment(String code, BigDecimal value, String type, String targetComponentCode, boolean isFullBreach, String tierCode) {
        this.adjustments.put(code, BundleAdjustment.builder()
                .value(value)
                .type(type)
                .targetComponentCode(targetComponentCode)
                .applyChargeOnFullBreach(isFullBreach)
                .matchedTierCode(tierCode)
                .build());
    }
}