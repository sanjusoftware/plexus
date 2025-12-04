package com.bankengine.rules.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Drools Fact used by the BundleRulesEngineService to calculate adjustments (discounts/waivers).
 * It holds the aggregated context and accumulates adjustments.
 */
@Data
@NoArgsConstructor
public class BundlePricingInput {

    // Input facts used by the DRL conditions (LHS)
    private String bankId;
    private String customerSegment;
    private BigDecimal grossTotalAmount;

    // Map for custom runtime facts defined in PricingInputMetadata
    private Map<String, Object> customAttributes = new HashMap<>();

    // Output map updated by the DRL rules (RHS)
    // Key: adjustment code, Value: monetary amount (negative for deductions)
    private final Map<String, BigDecimal> adjustments = new HashMap<>();

    /**
     * Constructor to satisfy the compiler error. (Resolves Error 1)
     */
    public BundlePricingInput(String bankId, String customerSegment, BigDecimal grossTotalAmount) {
        this.bankId = bankId;
        this.customerSegment = customerSegment;
        this.grossTotalAmount = grossTotalAmount;
    }

    /**
     * Helper method used by the DRL to apply an adjustment.
     * The rule logic will call $input.addAdjustment(...)
     */
    public void addAdjustment(String adjustmentCode, BigDecimal amount, String description) {
        this.adjustments.put(adjustmentCode, amount);
        // Note: The description parameter is currently ignored but kept for DRL readability.
    }

    /**
     * Calculates the final net total amount. (Resolves Error 2)
     */
    public BigDecimal getNetTotalAmount() {
        // Start with the gross total
        BigDecimal netTotal = this.grossTotalAmount != null ? this.grossTotalAmount : BigDecimal.ZERO;

        // Sum up all adjustments (which are typically negative for discounts/waivers)
        BigDecimal totalAdjustments = adjustments.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return netTotal.add(totalAdjustments);
    }
}