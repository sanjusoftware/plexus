package com.bankengine.catalog.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class ProductResponseDto {
    private Long id;
    private String name;
    private String productTypeName; // Return name instead of the entire ProductType object
    private String bankId;
    private LocalDate effectiveDate;
    private String status;
    private List<ProductFeatureLinkDto> features; // List of linked features (requires a nested DTO)
}