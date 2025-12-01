package com.bankengine.pricing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The final response containing the results of the two-stage pricing calculation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsolidatedPriceResponse {

    private String bankId;

    // 1. STAGE 1 OUTPUT: Sum of all component gross prices
    private BigDecimal grossTotalAmount;

    // 2. STAGE 2 OUTPUT: Adjustments applied by the Bundle Rules Engine
    // Key: Rule/Adjustment Code (e.g., "PREMIUM_WAIVER"), Value: Adjustment Amount (Negative for deduction)
    private Map<String, BigDecimal> adjustments;

    // 3. FINAL RESULT: Gross Total + Sum(Adjustments)
    private BigDecimal netTotalAmount;

    // Detailed breakdown of pricing for each product in the request
    private List<ProductPriceResultDto> productResults;
}