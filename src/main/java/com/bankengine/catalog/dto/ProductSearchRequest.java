package com.bankengine.catalog.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ProductSearchRequest {

    // Metadata Filters
    private String name; // Partial match (like %name%)
    private String bankId;
    private Long productTypeId;

    // Status Filter
    private String status; // e.g., "ACTIVE", "DRAFT", "INACTIVE", "ARCHIVED"

    // Effective Date Range Filters (for products active within a period)
    private LocalDate effectiveDateFrom;
    private LocalDate effectiveDateTo;

    // Pagination/Sorting
    private int page = 0;
    private int size = 10;
    private String sortBy = "id";
    private String sortDirection = "ASC";
}