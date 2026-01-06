package com.bankengine.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class ProductBundleRequest {

    @NotBlank(message = "Bundle code is required.")
    private String code;

    @NotBlank(message = "Bundle name is required.")
    private String name;

    private String description;

    @NotBlank(message = "Eligibility segment is required (e.g., Retail, SME).")
    private String eligibilitySegment;

    @NotNull(message = "Activation date is required.")
    private LocalDate activationDate;

    private LocalDate expiryDate;

    @Valid
    @NotEmpty(message = "A bundle must contain at least one product.")
    private List<BundleItemRequest> items;

    /**
     * Nested DTO for defining product relationships within the bundle.
     */
    @Data
    public static class BundleItemRequest {

        @NotNull(message = "Product ID is required for linking.")
        private Long productId;

        // Defines if the product is designated as the primary account in the bundle
        private boolean isMainAccount = false;

        // Defines if the product is required to be taken with the bundle
        private boolean isMandatory = true;
    }
}