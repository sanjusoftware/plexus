package com.bankengine.catalog.dto;

import com.bankengine.pricing.model.PriceValue;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPricing {

    @NotNull(message = "Pricing Component ID is required.")
    private Long pricingComponentId;
    private String pricingComponentName;
    private BigDecimal fixedValue;
    private PriceValue.ValueType fixedValueType;
    private boolean useRulesEngine;
    private String targetComponentCode;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
}