package com.bankengine.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateProductRequestDto {

    @NotBlank(message = "Product name is required.")
    @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters.")
    private String name;

    @NotBlank(message = "Bank ID is required.")
    private String bankId;

    @NotNull(message = "Product Type ID is required.")
    private Long productTypeId;

    @NotNull(message = "Effective Date is required.")
    private LocalDate effectiveDate;

    @NotBlank(message = "Status is required (e.g., DRAFT, ACTIVE).")
    private String status;

    private LocalDate expirationDate;
}