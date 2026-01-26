package com.bankengine.catalog.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ProductComparisonView {
    private List<ProductCatalogCard> products;
    private Map<String, List<String>> featureComparison; // Feature name -> [Product1 Value, Product2 Value, ...]
    private Map<String, List<String>> pricingComparison; // Pricing item -> [Product1 Price, Product2 Price, ...]
}
