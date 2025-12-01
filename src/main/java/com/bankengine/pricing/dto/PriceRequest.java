package com.bankengine.pricing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceRequest {

    // --- Core Catalog Input ---
    @NotNull(message = "Product ID is mandatory for pricing.")
    private Long productId;

    // Optional: If pricing depends on the parent bundle (e.g., a discounted rate only applies in Bundle X)
    private Long productBundleId;

    // --- Transactional Inputs ---
    @NotNull(message = "Amount is mandatory for fee/rate calculation.")
    private BigDecimal amount;

    // Optional: Date to check for rate/fee applicability (default to today)
    @Builder.Default
    private LocalDate effectiveDate = LocalDate.now();

    // --- Customer/Bank Context ---
    @NotNull(message = "Customer Segment is mandatory for pricing rule matching.")
    private String customerSegment; // e.g., "RETAIL", "SME", "PREMIUM"

}