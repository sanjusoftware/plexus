package com.bankengine.pricing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class UpdatePricingTierRequestDto {

    @NotBlank(message = "Tier name is required.")
    private String tierName;

    @NotNull(message = "Tier conditions list cannot be null.")
     private List<TierConditionDto> conditions;

    // Threshold fields (optional, but included for update)
    private BigDecimal minThreshold;
    private BigDecimal maxThreshold;
}