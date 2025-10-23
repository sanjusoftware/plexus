package com.bankengine.pricing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class CreatePriceValueRequestDto {

    @NotNull(message = "Price amount is required.")
    private BigDecimal priceAmount;

    @NotBlank(message = "Value type is required (e.g., ABSOLUTE, PERCENTAGE).")
    private String valueType;

    @NotBlank(message = "Currency is required (e.g., USD, EUR).")
    private String currency;
}