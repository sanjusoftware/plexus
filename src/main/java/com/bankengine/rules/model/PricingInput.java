package com.bankengine.rules.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * The primary fact object inserted into the Drools working memory for pricing calculation.
 * Rules operate against its attributes to determine the final PriceValue result.
 */
@Data
public class PricingInput {

    // --- Input fields used in TierCondition comparisons ---
    private String customerSegment;
    private BigDecimal transactionAmount;
    private String clientType;
    private Boolean isNewCustomer;
    // Add other relevant input fields here (e.g., productCode, region, tenure)

    // --- Output fields updated by the Drools Rules (RHS) ---
    private Long matchedTierId;
    private BigDecimal priceAmount;
    private String valueType; // Maps to PriceValue.ValueType enum name
    private String currency;
    private boolean ruleFired = false;
}