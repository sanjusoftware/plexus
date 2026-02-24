package com.bankengine.pricing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingComponentRequest {

    @NotBlank(message = "Component name is required.")
    private String name;
    @NotBlank(message = "Component type is required (e.g., FEE, RATE).")
    private String type;
    private String description;
    @Valid
    private List<PricingTierRequest> pricingTiers;
}