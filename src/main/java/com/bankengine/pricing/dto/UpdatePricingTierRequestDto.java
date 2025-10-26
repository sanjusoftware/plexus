package com.bankengine.pricing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdatePricingTierRequestDto {

    @NotBlank(message = "Tier name is required.")
    private String tierName;

    // Condition fields (optional, but included for update)
    private String conditionKey;
    private String conditionValue;

    // Threshold fields (optional, but included for update)
    private BigDecimal minThreshold;
    private BigDecimal maxThreshold;
}