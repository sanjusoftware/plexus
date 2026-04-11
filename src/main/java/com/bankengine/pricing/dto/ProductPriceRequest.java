package com.bankengine.pricing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPriceRequest {

    @NotNull(message = "Product ID is mandatory.")
    private Long productId;

    // "Pro-rata First Month".
    private java.time.LocalDate enrollmentDate;

    // "External Facility Counters".
    // Maps like: {"customerSegment":"CORPORATE","transactionAmount":1000,"effectiveDate":"2026-04-11"}
    private Map<String, Object> customAttributes;
}