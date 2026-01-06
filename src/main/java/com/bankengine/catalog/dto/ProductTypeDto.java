package com.bankengine.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductTypeDto {

    private Long id; // Null for Requests, populated for Responses

    @NotBlank(message = "Product Type name is required.")
    @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters.")
    private String name; // e.g., "CASA", "Credit Card", "Loan (Secured)"
}