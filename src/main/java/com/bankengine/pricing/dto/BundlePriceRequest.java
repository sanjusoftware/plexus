package com.bankengine.pricing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class BundlePriceRequest {

    @NotNull(message = "Product Bundle ID is mandatory for pricing.")
    private Long productBundleId;

    @NotNull(message = "A list of products is mandatory for bundle calculation.")
    private List<ProductRequest> products;

    // --- Contextual Inputs ---
    @NotNull(message = "Customer Segment is mandatory for pricing rule matching.")
    private String customerSegment;

    private LocalDate effectiveDate = LocalDate.now();

    @Data
    public static class ProductRequest {
        @NotNull
        private Long productId;
        @NotNull
        private BigDecimal amount; // Transactional amount relevant to this product
    }
}