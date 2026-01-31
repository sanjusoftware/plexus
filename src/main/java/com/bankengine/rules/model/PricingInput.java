package com.bankengine.rules.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Data
public class PricingInput {
    private String bankId;
    private LocalDate referenceDate = LocalDate.now();

    private Set<Long> targetPricingComponentIds;
    private Set<Long> activePricingTierIds;
    private Map<String, Object> customAttributes = new HashMap<>();

    // --- Output fields updated by the Drools Rules (RHS) ---
    private Long matchedTierId;
    private BigDecimal priceAmount;
    private String valueType;
    private boolean ruleFired = false;
}