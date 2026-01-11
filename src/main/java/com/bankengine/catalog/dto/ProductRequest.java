package com.bankengine.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {

    @NotBlank(message = "Product name is required.")
    @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters.")
    private String name;

    @NotNull(message = "Product Type ID is required.")
    private Long productTypeId;

    @NotNull(message = "Effective Date is required.")
    private LocalDate effectiveDate;

    @NotBlank(message = "Status is required.")
    private String status;

    private LocalDate expirationDate;

    @NotBlank(message = "Product category is required (e.g., RETAIL, WEALTH, CORPORATE).")
    private String category;
}