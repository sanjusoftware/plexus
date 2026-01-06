package com.bankengine.catalog.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProductTypeResponse {
    private Long id;
    private String name;
}