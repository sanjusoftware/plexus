package com.bankengine.rules.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Data
public class PricingInput {

    // Identifies the tenant
    private String bankId;

    // The set of PricingComponent IDs that Drools should evaluate.
    // Rules should check: "eval(input.getTargetPricingComponentIds().contains(componentId))"
    private Set<Long> targetPricingComponentIds;

    // Contextual data (productId, customerSegment, transactionAmount, etc.)
    private Map<String, Object> customAttributes = new HashMap<>();

    // --- Output fields updated by the Drools Rules (RHS) ---
    private Long matchedTierId;
    private BigDecimal priceAmount;
    private String valueType;
    private boolean ruleFired = false;
}