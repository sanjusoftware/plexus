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
    // Maps like: {"CUSTOMER_SEGMENT":"CORPORATE","TRANSACTION_AMOUNT":1000,"EFFECTIVE_DATE":"2026-04-11"}
    private Map<String, Object> customAttributes;
}