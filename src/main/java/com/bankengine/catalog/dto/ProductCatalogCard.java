package com.bankengine.catalog.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ProductCatalogCard {
    private Long productId;
    private String productName;
    private String productTypeDisplayName;
    private String category;
    private String tagline; // e.g., "High Interest Savings Account"
    private String iconUrl; // For UI display

    // Simplified feature highlights (top 3-5 features)
    private List<FeatureHighlight> keyFeatures;

    // Simplified pricing summary
    private PricingSummary pricingSummary;

    // Eligibility indicator
    private boolean eligibleForCustomer;
    private String eligibilityMessage; // e.g., "Available for Premium customers"

    @Data
    @Builder
    public static class FeatureHighlight {
        private String featureName;
        private String displayValue;
        private String icon; // e.g., "percentage", "calendar", "checkmark"
    }

    @Data
    @Builder
    public static class PricingSummary {
        private String mainPriceLabel; // e.g., "Monthly Fee"
        private BigDecimal mainPriceValue;
        private String priceDescription; // e.g., "Waived with $5,000 min balance"
        private List<String> additionalFees; // e.g., ["ATM Withdrawal: $2.00", "Wire Transfer: $15.00"]
    }
}
