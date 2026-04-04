package com.bankengine.pricing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Schema(description = "The single persisted value attached to a pricing tier.")
public class PriceValueRequest {

    @NotNull(message = "Price amount is required.")
    @Schema(example = "15.00", description = "Numeric amount or percentage value associated with the tier.")
    private BigDecimal priceAmount;

    @NotBlank(message = "Value Type is required (e.g., ABSOLUTE, PERCENTAGE).")
    @Schema(example = "FEE_ABSOLUTE", description = "Allowed values: FEE_ABSOLUTE, FEE_PERCENTAGE, DISCOUNT_PERCENTAGE, DISCOUNT_ABSOLUTE, FREE_COUNT. Choose a fee type for fee/rate components and a discount/free type for discount-style components.")
    private String valueType;
}