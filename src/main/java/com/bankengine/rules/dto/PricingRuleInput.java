package com.bankengine.rules.dto;

import com.bankengine.pricing.model.PricingTier;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Data
@Getter
@Setter
@NoArgsConstructor
public class PricingRuleInput {
    // Input Facts
    private String customerSegment;
    private BigDecimal transactionAmount;
    private List<PricingTier> availableTiers; // The pool of tiers to choose from

    // Output Placeholder (The decision)
    private Long matchedTierId = null; // Rule will set this if a match is found
    private boolean ruleFired = false; // Flag to indicate a successful match
}