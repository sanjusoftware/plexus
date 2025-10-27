package com.bankengine.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal; // Assuming price is a BigDecimal

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductPricingLinkDto {
    private String pricingComponentName; // e.g., "Monthly Fee", "Minimum Balance"
    private String context;
    // Include other necessary fields from ProductPricingLink entity if needed
}