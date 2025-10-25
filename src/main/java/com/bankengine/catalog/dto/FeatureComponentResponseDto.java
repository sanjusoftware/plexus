package com.bankengine.catalog.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FeatureComponentResponseDto {
    private Long id;
    private String name;
    private String dataType;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}