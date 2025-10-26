// src/main/java/com/bankengine/pricing/dto/UpdatePriceValueRequestDto.java

package com.bankengine.pricing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdatePriceValueRequestDto {

    @NotNull(message = "Price amount is required.")
    private BigDecimal priceAmount;

    @NotBlank(message = "Currency is required (e.g., USD, EUR).")
    private String currency;

    @NotBlank(message = "Value Type is required (e.g., ABSOLUTE, PERCENTAGE).")
    private String valueType;
}