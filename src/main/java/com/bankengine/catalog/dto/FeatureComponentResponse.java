package com.bankengine.catalog.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FeatureComponentResponse {
    private Long id;
    private String name;
    private String dataType;
    private String code;
    private Integer version;
    private String status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}