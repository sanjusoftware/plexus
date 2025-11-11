package com.bankengine.rules.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Data
public class PricingInput {

    // Example: customAttributes.put("transactionAmount", new BigDecimal("150.00"));
    private Map<String, Object> customAttributes = new HashMap<>();

    // --- Output fields updated by the Drools Rules (RHS) ---
    private Long matchedTierId;
    private BigDecimal priceAmount;
    private String valueType;
    private String currency;
    private boolean ruleFired = false;
}