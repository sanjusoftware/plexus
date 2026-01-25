package com.bankengine.catalog.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class ProductBundleResponse {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String eligibilitySegment;
    private LocalDate activationDate;
    private LocalDate expiryDate;
    private String status;
    private List<BundleItemResponse> items;
    private List<BundlePricingResponse> pricing;

    @Data
    public static class BundleItemResponse {
        private ProductResponse product;
        private boolean isMainAccount;
        private boolean isMandatory;
    }
}
