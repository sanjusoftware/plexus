package com.bankengine.pricing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingComponentRequest {

    @NotBlank(message = "Component name is required.")
    private String name;

    @NotBlank(message = "Component type is required (e.g., FEE, RATE).")
    private String type;
}