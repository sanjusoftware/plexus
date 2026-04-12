package com.bankengine.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCategoryDto {
    private Long id;
    private String code;
    private String name;
    private boolean archived;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
