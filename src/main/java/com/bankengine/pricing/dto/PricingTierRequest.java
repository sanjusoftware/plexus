package com.bankengine.pricing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(example = "Standard Tier", description = "Display name for the pricing tier.")
    private String name;

    @NotBlank(message = "Tier code is required.")
    @Schema(example = "STD_TIER", description = "Unique code for the pricing tier.")
    private String code;

    @Schema(example = "1", description = "Priority of the tier during rule evaluation. Higher numbers represent higher priority. Default is a very low value (last priority).")
    private Integer priority;

    @Schema(example = "0.00", description = "Minimum threshold for the tier (e.g., minimum balance).")
    private BigDecimal minThreshold;

    @Schema(example = "10000.00", description = "Maximum threshold for the tier (e.g., maximum balance).")
    private BigDecimal maxThreshold;

    @Builder.Default
    @Schema(example = "2024-01-01", description = "The date from which this tier becomes effective.")
    private LocalDate effectiveDate = LocalDate.now();

    @Schema(example = "2025-12-31", description = "The date on which this tier expires.")
    private LocalDate expiryDate;

    @Schema(example = "false", description = "If true, the full amount is charged if the limit is breached. If false, only the portion exceeding the limit might be charged (depending on business logic).")
    private boolean applyChargeOnFullBreach;

    @Builder.Default
    @Schema(description = "List of conditions that must be met for this tier to apply.")
    private List<TierConditionDto> conditions = new ArrayList<>();

    @NotNull(message = "Price value is required for the tier.")
    @Valid
    @Schema(description = "The actual price or discount value associated with this tier.")
    private PriceValueRequest priceValue;
}
