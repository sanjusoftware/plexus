package com.bankengine.rules.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Data
public class PricingInput {

    // Identifies the tenant
    private String bankId;

    /**
     * The date used for temporal evaluation within the rules.
     * Defaults to today if not provided.
     */
    private LocalDate referenceDate = LocalDate.now();

    private Set<Long> targetPricingComponentIds;

    // Contextual data (productId, customerSegment, transactionAmount, etc.)
    private Map<String, Object> customAttributes = new HashMap<>();

    // --- Output fields updated by the Drools Rules (RHS) ---
    private Long matchedTierId;
    private BigDecimal priceAmount;
    private String valueType;
    private boolean ruleFired = false;
}