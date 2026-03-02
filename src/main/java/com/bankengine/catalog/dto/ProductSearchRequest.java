package com.bankengine.catalog.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ProductSearchRequest {

    // Metadata Filters
    private String name;
    private String code;
    private Long productTypeId;
    private String category;

    // Status Filter
    private String status;

    // Effective Date Range Filters
    private LocalDate activationDateFrom;
    private LocalDate activationDateTo;

    // Pagination/Sorting
    private int page = 0;
    private int size = 10;
    private String sortBy = "version";
    private String sortDirection = "DESC";
}