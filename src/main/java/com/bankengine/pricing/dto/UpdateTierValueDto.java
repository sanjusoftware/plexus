package com.bankengine.pricing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateTierValueDto {

    @Valid
    @NotNull(message = "Tier details must be provided.")
    private PricingTierRequest tier;

    @Valid
    @NotNull(message = "Value details must be provided.")
    private PriceValueRequest value;
}