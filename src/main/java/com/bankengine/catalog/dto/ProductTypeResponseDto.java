package com.bankengine.catalog.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProductTypeResponseDto {
    private Long id;
    private String name;
}