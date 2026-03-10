package com.bankengine.pricing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Response object for pricing input metadata.")
public class PricingMetadataResponse {

    private Long id;

    private String attributeKey;

    private String displayName;

    private String dataType;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
