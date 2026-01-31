package com.bankengine.pricing.dto;

import jakarta.validation.constraints.NotBlank;
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
    private String tierName;

    private BigDecimal minThreshold;
    private BigDecimal maxThreshold;

    // Temporal versioning fields
    private LocalDate effectiveDate;
    private LocalDate expiryDate;

    // Calculation logic flags
    private boolean proRataApplicable;
    private boolean applyChargeOnFullBreach;

    @Builder.Default
    private List<TierConditionDto> conditions = new ArrayList<>();
}