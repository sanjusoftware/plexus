package com.bankengine.pricing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * The aggregated pricing result for a single product within the bundle.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPriceResultDto {

    private Long productId;
    private String productName;

    // The sum of all component prices for this product (Gross Price before bundle adjustments)
    private BigDecimal grossPrice;

    // The detailed price results for each component (calculated by PricingCalculationService)
    private List<PriceValueResponseDto> componentPrices;
}