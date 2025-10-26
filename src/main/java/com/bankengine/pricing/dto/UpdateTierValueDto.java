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
    private UpdatePricingTierRequestDto tier;

    @Valid
    @NotNull(message = "Value details must be provided.")
    private UpdatePriceValueRequestDto value;
}