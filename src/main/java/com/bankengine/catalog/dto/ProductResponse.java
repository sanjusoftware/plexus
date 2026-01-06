package com.bankengine.catalog.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductResponse {
    private Long id;
    private String name;
    private String bankId;
    private LocalDate effectiveDate;
    private String status;
    private ProductTypeResponse productType;
    private LocalDate expirationDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ProductFeature> features;
    private List<ProductPricing> pricing;
}