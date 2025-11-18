package com.bankengine.pricing.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetadataResponseDto {

    private Long id; // Internal database ID

    private String attributeKey;

    private String displayName;

    private String dataType;

    // Optional fields for tracking/auditing
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}