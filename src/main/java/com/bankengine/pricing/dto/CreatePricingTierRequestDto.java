package com.bankengine.pricing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Set;

@Data
public class CreatePricingTierRequestDto {

    @NotBlank(message = "Tier name is required.")
    private String tierName;

    // Threshold Fields (Dedicated for numeric range/tiering)
    private BigDecimal minThreshold;
    private BigDecimal maxThreshold;

    /**
     * Optional list of complex conditions (Dedicated for contextual/segmentation rules) to be applied to this tier.
     */
    private Set<TierConditionDto> conditions;
}