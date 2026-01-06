package com.bankengine.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductPricingResponse {
    private String pricingComponentName; // e.g., "Monthly Fee", "Minimum Balance"
    private String context;
}