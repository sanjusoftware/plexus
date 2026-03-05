package com.bankengine.pricing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingTierRequest {
    @NotBlank(message = "Tier name is required.")
    private String name;

    @NotBlank(message = "Tier code is required.")
    private String code;

    private int priority;

    private BigDecimal minThreshold;
    private BigDecimal maxThreshold;

    @Builder.Default
    private LocalDate effectiveDate = LocalDate.now();
    private LocalDate expiryDate;
    private boolean applyChargeOnFullBreach;

    @Builder.Default
    private List<TierConditionDto> conditions = new ArrayList<>();

    @NotNull(message = "Price value is required for the tier.")
    @Valid
    private PriceValueRequest priceValue;
}