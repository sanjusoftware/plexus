package com.bankengine.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPricingRequest {

    @NotNull(message = "Pricing Component ID is required.")
    private Long pricingComponentId;

    @NotBlank(message = "Context (e.g., CORE_RATE, ANNUAL_FEE) is required for the pricing link.")
    private String context;
}