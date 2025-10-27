package com.bankengine.catalog.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductResponseDto {
    private Long id;
    private String name;
    private String bankId;
    private LocalDate effectiveDate;
    private String status;
    private ProductTypeResponseDto productType;
    private LocalDate expirationDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ProductFeatureLinkDto> features;
    private List<ProductPricingLinkDto> pricing;
}