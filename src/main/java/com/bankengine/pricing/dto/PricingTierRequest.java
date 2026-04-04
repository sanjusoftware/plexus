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
@Schema(description = "Request payload for creating or updating a pricing tier nested under a pricing component.")
public class PricingTierRequest {
    @NotBlank(message = "Tier name is required.")
    @Schema(example = "Standard Tier", description = "Display name for the pricing tier.")
    private String name;

    @NotBlank(message = "Tier code is required.")
    @Schema(example = "STD_TIER", description = "Unique code for the pricing tier.")
    private String code;

    @Schema(example = "10", description = "Priority used during rule evaluation. Higher numbers are evaluated first. If omitted, the tier is assigned the lowest priority. Tiers with the same priority are treated at the same evaluation level.")
    private Integer priority;

    @Schema(example = "0.00", description = "Optional minimum threshold for the tier, for example minimum transaction amount or balance.")
    private BigDecimal minThreshold;

    @Schema(example = "10000.00", description = "Optional maximum threshold for the tier, for example maximum transaction amount or balance.")
    private BigDecimal maxThreshold;

    @Builder.Default
    @Schema(example = "2024-01-01", description = "The date from which this tier becomes effective.")
    private LocalDate effectiveDate = LocalDate.now();

    @Schema(example = "2025-12-31", description = "The date on which this tier expires.")
    private LocalDate expiryDate;

    @Schema(example = "false", description = "Controls how threshold breaches are applied. If true, the full amount is charged when the tier condition is breached. If false, downstream pricing logic may apply only the breached portion.")
    private boolean applyChargeOnFullBreach;

    @Builder.Default
    @Schema(description = "Conditions that must match for this tier to apply, such as customer segment, income band, or channel.")
    private List<TierConditionDto> conditions = new ArrayList<>();

    @NotNull(message = "Price value is required for the tier.")
    @Valid
    @Schema(description = "The actual value produced by this tier. The allowed valueType depends on the parent component type.")
    private PriceValueRequest priceValue;
}
