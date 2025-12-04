package com.bankengine.pricing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreatePriceValueRequestDto {

    @NotNull(message = "Price amount is required.")
    private BigDecimal priceAmount;

    @NotBlank(message = "Value type is required (e.g., ABSOLUTE, PERCENTAGE).")
    private String valueType;

}