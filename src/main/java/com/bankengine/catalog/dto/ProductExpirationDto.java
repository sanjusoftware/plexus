package com.bankengine.catalog.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

import java.time.LocalDate;

@Value
public class ProductExpirationDto {
    @NotNull(message = "New expiration date is required.")
    private LocalDate newExpirationDate;
}