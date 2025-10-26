// src/main/java/com/bankengine/pricing/dto/PricingComponentResponseDto.java
package com.bankengine.pricing.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PricingComponentResponseDto {
    private Long id;
    private String name;
    private String type;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}