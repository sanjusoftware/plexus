package com.bankengine.pricing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BundlePriceRequest {
    @NotNull(message = "Product Bundle ID is mandatory for pricing.")
    private Long productBundleId;

    @NotNull(message = "A list of products is mandatory for bundle calculation.")
    private List<BundleProductItem> products;

    private java.time.LocalDate enrollmentDate; // For pro-rata calculation

    private Map<String, Object> customAttributes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BundleProductItem {
        @NotNull
        private Long productId;
        @NotNull
        private BigDecimal transactionAmount;
    }
}