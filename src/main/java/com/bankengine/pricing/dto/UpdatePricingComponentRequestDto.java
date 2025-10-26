package com.bankengine.pricing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePricingComponentRequestDto {

    @NotBlank(message = "Pricing Component name is required.")
    private String name;

    @NotBlank(message = "Component Type is required (e.g., RATE, FEE).")
    private String type;
}