package com.bankengine.pricing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PriceValueRequest {

    @NotNull(message = "Price amount is required.")
    private BigDecimal priceAmount;

    @NotBlank(message = "Value Type is required (e.g., ABSOLUTE, PERCENTAGE).")
    private String valueType;
}