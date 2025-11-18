package com.bankengine.pricing.dto;

import lombok.Data;

@Data
public class PricingInputMetadataDto {
    private Long id;
    private String attributeKey;
    private String dataType;
    private String displayName;
}
