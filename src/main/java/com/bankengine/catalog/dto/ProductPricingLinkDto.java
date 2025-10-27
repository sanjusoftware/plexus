package com.bankengine.catalog.dto;

import lombok.Value;
import lombok.Builder;
import java.math.BigDecimal; // Assuming price is a BigDecimal

@Value
@Builder
public class ProductPricingLinkDto {
    private String pricingComponentName; // e.g., "Monthly Fee", "Minimum Balance"
    private BigDecimal value;
    // Include other necessary fields from ProductPricingLink entity if needed
}