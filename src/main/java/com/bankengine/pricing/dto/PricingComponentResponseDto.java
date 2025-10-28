package com.bankengine.pricing.dto;

import com.bankengine.pricing.model.PricingComponent.ComponentType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class PricingComponentResponseDto {
    private Long id;
    private String name;
    private ComponentType type;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PricingTierResponseDto> pricingTiers;
}