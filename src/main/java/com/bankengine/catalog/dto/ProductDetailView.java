package com.bankengine.catalog.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ProductDetailView {
    private Long productId;
    private String productName;
    private String fullDescription;
    private String category;
    private ProductTypeDto productType;

    // Organized feature sections
    private Map<String, List<ProductFeatureDetail>> featuresByCategory;

    // Full pricing breakdown
    private PricingBreakdown pricing;

    // Terms and conditions
    private String termsAndConditions;
    private LocalDate availableFrom;
    private LocalDate availableUntil;

    // Related products
    private List<ProductCatalogCard> relatedProducts;

    @Data
    @Builder
    public static class ProductFeatureDetail {
        private String featureName;
        private String value;
        private String description;
        private String displayCategory; // e.g., "Account Limits", "Interest Rates", "Services"
    }

    @Data
    @Builder
    public static class PricingBreakdown {
        private List<PricingItem> fees;
        private List<PricingItem> rates;
        private List<PricingItem> waivers;
        private String pricingNote; // e.g., "Pricing varies by customer segment"

        @Data
        @Builder
        public static class PricingItem {
            private String name;
            private String value;
            private String condition; // e.g., "for transactions > $1,000"
            private boolean highlighted;
        }
    }
}
