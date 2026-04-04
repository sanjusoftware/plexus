package com.bankengine.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
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
@Schema(description = "Request object for creating or updating a product aggregate, including its features and pricing links.")
public class ProductRequest {

    @NotBlank(message = "Product code is required.")
    @Schema(example = "SAV-STD-001", description = "Unique business code for the product.")
    private String code;

    @NotBlank(message = "Product name is required.")
    @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters.")
    @Schema(example = "Standard Savings Account", description = "Marketing name of the product.")
    private String name;

    @NotBlank(message = "Product type code is required.")
    @Schema(example = "CASA", description = "Code of the associated Product Type.")
    private String productTypeCode;

    @NotBlank(message = "Product category is required (e.g., RETAIL, WEALTH, CORPORATE).")
    @Schema(example = "RETAIL", description = "Primary business category. Examples: RETAIL, WEALTH, CORPORATE, INVESTMENT")
    private String category;

    @FutureOrPresent(message = "Activation date cannot be in the past.")
    @Schema(example = "2024-06-01")
    private LocalDate activationDate;

    @Future(message = "Expiry date must be in the future.")
    @Schema(example = "2030-12-31")
    private LocalDate expiryDate;

    @Builder.Default
    @Schema(description = "List of features to be linked to this product.")
    @Valid
    private List<ProductFeatureDto> features = new java.util.ArrayList<>();

    @Builder.Default
    @Schema(description = "List of pricing components to be linked to this product.")
    @Valid
    private List<ProductPricingDto> pricing = new java.util.ArrayList<>();

    @Schema(example = "Grow your wealth with us", description = "Short tagline for display.")
    private String tagline;

    @Schema(example = "A comprehensive savings account with high interest and low fees.", description = "Detailed product description.")
    private String fullDescription;

    @Schema(example = "https://plexus.bank.com/icons/savings.png")
    private String iconUrl;

    @Schema(example = "1")
    private Integer displayOrder;

    @Schema(example = "true")
    private boolean isFeatured;

    @Schema(example = "RETAIL, STUDENT", description = "Comma-separated target segments.")
    private String targetCustomerSegments;

    @Schema(example = "Standard terms and conditions apply.", description = "Legal terms for the product.")
    private String termsAndConditions;
}
