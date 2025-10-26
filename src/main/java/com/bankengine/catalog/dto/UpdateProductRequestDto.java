package com.bankengine.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateProductRequestDto {

    // Fields allowed for in-place update (metadata)
    @NotBlank(message = "Product name is required.")
    private String name;

    private LocalDate effectiveDate;
    private LocalDate expirationDate;

    @NotBlank(message = "Bank ID is required.")
    private String bankId;

    @NotBlank(message = "Status is required (e.g., ACTIVE, DRAFT, INACTIVE).")
    private String status;
}