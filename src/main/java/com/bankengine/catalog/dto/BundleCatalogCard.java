package com.bankengine.catalog.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class BundleCatalogCard {
    private Long bundleId;
    private String name;
    private String description;
    private String eligibilitySegment;

    // The "Member Products" within the bundle
    private List<BundleItemDetail> items;

    // Pricing calculated via BundlePricingService
    private BundlePricingSummary pricing;

    @Data
    @Builder
    public static class BundleItemDetail {
        private String productName;
        private String productCategory;
        private boolean isMandatory;
        private boolean isMainAccount;
    }

    @Data
    @Builder
    public static class BundlePricingSummary {
        private BigDecimal totalMonthlyFee;    // netTotalAmount
        private BigDecimal totalSavings;       // Sum of all DISCOUNT/WAIVER adjustments
        private List<String> adjustmentLabels; // e.g., ["50% Off Debit Card", "Free ATM Withdrawal"]
    }
}