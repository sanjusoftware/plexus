package com.bankengine.catalog.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductBundleResponse {
    private Long id;
    private String name;
    private String code;
    private Integer version;
    private String bankId;
    private LocalDate activationDate;
    private LocalDate expiryDate;
    private String status;
    private String description;
    private String eligibilitySegment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Nested links to constituents
    private List<BundleProductDto> products;
    private List<ProductPricing> pricing;

    @Data
    public static class BundleProductDto {
        private Long productId;
        private String productName;
        private String productCode;
        private boolean mainAccount;
        private boolean mandatory;
    }

    @Data
    public static class ProductPricing {
        private Long pricingComponentId;
        private String pricingComponentName;
        private String targetComponentCode;

        // Fields from BundlePricingLink
        private BigDecimal fixedValue;
        private String fixedValueType;
        private boolean useRulesEngine;
        private LocalDate effectiveDate;
        private LocalDate expiryDate;
    }
}