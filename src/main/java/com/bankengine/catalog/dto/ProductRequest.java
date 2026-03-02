package com.bankengine.catalog.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {

    @NotBlank(message = "Product code is required.")
    private String code;

    @NotBlank(message = "Product name is required.")
    @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters.")
    private String name;

    @NotNull(message = "Product Type ID is required.")
    private Long productTypeId;

    @NotBlank(message = "Product category is required (e.g., RETAIL, WEALTH, CORPORATE).")
    private String category;

    @FutureOrPresent(message = "Activation date cannot be in the past.")
    private LocalDate activationDate;

    @Future(message = "Expiry date must be in the future.")
    private LocalDate expiryDate;

    @Builder.Default
    private List<ProductFeatureDto> features = new java.util.ArrayList<>();

    @Builder.Default
    private List<ProductPricingDto> pricing = new java.util.ArrayList<>();

    private String tagline;
    private String fullDescription;
    private String iconUrl;
    private Integer displayOrder;
    private boolean isFeatured;
    private String targetCustomerSegments;
    private String termsAndConditions;
}