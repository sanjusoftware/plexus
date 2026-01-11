package com.bankengine.pricing.dto;

import com.bankengine.pricing.model.PricingComponent.ComponentType;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
@Jacksonized
public class PricingComponentResponse {
    private Long id;
    private String name;
    private ComponentType type;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private List<PricingTierResponse> pricingTiers;
}