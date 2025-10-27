package com.bankengine.catalog.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class ProductExpirationDto {
    @NotNull(message = "Expiration date cannot be null.")
    private LocalDate expirationDate;
}
