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
@Schema(description = "Request object for creating or updating a product bundle aggregate.")
public class ProductBundleRequest {

    @NotBlank(message = "Bundle code is required.")
    @Size(max = 50, message = "Bundle code must not exceed 50 characters.")
    @Schema(example = "BND-PREMIUM-001", description = "Unique business code for the bundle.")
    private String code;

    @NotBlank(message = "Bundle name is required.")
    @Size(min = 3, max = 100, message = "Bundle name must be between 3 and 100 characters.")
    @Schema(example = "Premium Banking Bundle", description = "Marketing name of the bundle.")
    private String name;

    @Size(max = 500, message = "Description is too long.")
    @Schema(example = "A bundle designed for high-net-worth individuals, including a checking account and a wealth management product.")
    private String description;

    @NotBlank(message = "Target customer segments is required (e.g., Retail,SME).")
    @Schema(example = "RETAIL,WEALTH", description = "Comma-separated target segments. These are used to match eligibility rules.")
    private String targetCustomerSegments;

    @FutureOrPresent(message = "Activation date cannot be in the past.")
    @Schema(example = "2024-06-01")
    private LocalDate activationDate;

    @Future(message = "Expiry date must be in the future.")
    @Schema(example = "2030-12-31")
    private LocalDate expiryDate;

    @Valid
    @NotEmpty(message = "A bundle must contain at least one product.")
    @Builder.Default
    @Schema(description = "List of products to be included in this bundle.")
    private List<BundleProduct> products = new java.util.ArrayList<>();

    @Valid
    @Builder.Default
    @Schema(description = "Bundle-level pricing adjustments.")
    private List<ProductPricingDto> pricing = new java.util.ArrayList<>();

    @Data
    @Schema(description = "Link between a bundle and a product.")
    public static class BundleProduct {
        @NotNull(message = "Product ID is required for linking.")
        @Schema(example = "500", description = "ID of the product to include.")
        private Long productId;

        @Schema(example = "true", description = "If true, this product acts as the primary account for the bundle.")
        private boolean mainAccount = false;

        @Schema(example = "true", description = "If true, this product must be present in the bundle.")
        private boolean mandatory = true;
    }
}