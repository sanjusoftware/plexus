package com.bankengine.catalog.dto;

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
public class ProductBundleRequest {

    @NotBlank(message = "Bundle code is required.")
    @Size(max = 50, message = "Bundle code must not exceed 50 characters.")
    private String code;

    @NotBlank(message = "Bundle name is required.")
    @Size(min = 3, max = 100, message = "Bundle name must be between 3 and 100 characters.")
    private String name;

    @Size(max = 500, message = "Description is too long.")
    private String description;

    @NotBlank(message = "Target customer segments is required (e.g., Retail,SME).")
    private String targetCustomerSegments;

    @FutureOrPresent(message = "Activation date cannot be in the past.")
    private LocalDate activationDate;

    @Future(message = "Expiry date must be in the future.")
    private LocalDate expiryDate;

    @Valid
    @NotEmpty(message = "A bundle must contain at least one product.")
    @Builder.Default
    private List<BundleProduct> products = new java.util.ArrayList<>();

    @Valid
    @Builder.Default
    private List<ProductPricingDto> pricing = new java.util.ArrayList<>();

    @Data
    public static class BundleProduct {
        @NotNull(message = "Product ID is required for linking.")
        private Long productId;
        private boolean mainAccount = false;
        private boolean mandatory = true;
    }
}