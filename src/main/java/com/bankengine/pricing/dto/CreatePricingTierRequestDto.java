package com.bankengine.pricing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class CreatePricingTierRequestDto {

    @NotBlank(message = "Tier name is required.")
    private String tierName;

    // These can be null for unconditional tiers, but we'll validate if they exist.
    private String conditionKey;
    private String conditionValue;

    // Thresholds
    private BigDecimal minThreshold;
    private BigDecimal maxThreshold;
}