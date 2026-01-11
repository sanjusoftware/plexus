package com.bankengine.catalog.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPricing {

    @NotNull(message = "Pricing Component ID is required.")
    private Long pricingComponentId;
    private String pricingComponentName;
}