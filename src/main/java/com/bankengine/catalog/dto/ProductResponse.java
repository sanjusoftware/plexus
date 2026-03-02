package com.bankengine.catalog.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductResponse {
    private Long id;
    private String name;
    private String code;
    private Integer version;
    private String bankId;
    private LocalDate activationDate;
    private LocalDate expiryDate;
    private String status;
    private String category;
    private ProductTypeDto productType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Marketing/Display fields
    private String tagline;
    private String fullDescription;
    private String iconUrl;
    private Integer displayOrder;
    private boolean featured;

    // Nested Collections (FAT DTO)
    private List<ProductFeatureDto> features;
    private List<ProductPricingDto> pricing;
}