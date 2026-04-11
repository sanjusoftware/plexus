package com.bankengine.rules.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Data
public class PricingInput {
    private String bankId;
    private Set<String> targetPricingComponentCodes;
    private Set<String> activePricingTierCodes;
    private Map<String, Object> customAttributes = new HashMap<>();

    // --- Output fields updated by the Drools Rules (RHS) ---
    private Long matchedTierId;
    private BigDecimal priceAmount;
    private String valueType;
    private boolean ruleFired = false;
}