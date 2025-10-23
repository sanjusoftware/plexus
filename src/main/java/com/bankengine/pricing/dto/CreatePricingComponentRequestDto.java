package com.bankengine.pricing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreatePricingComponentRequestDto {

    @NotBlank(message = "Component name is required.")
    private String name;

    @NotBlank(message = "Component type is required (e.g., FEE, RATE).")
    private String type; // Expects a string that maps to PricingComponent.ComponentType enum
}