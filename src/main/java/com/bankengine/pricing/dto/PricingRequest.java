package com.bankengine.pricing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingRequest {

    @NotNull(message = "Product ID is mandatory.")
    private Long productId;

    private Long productBundleId;

    private BigDecimal amount;

    // "Mid-Cycle Changes".
    // Use this to fetch the price that was active on a specific date (e.g. start of billing cycle).
    @Builder.Default
    private LocalDate referenceDate = LocalDate.now();

    // Optional: Date to check for rate/fee applicability (default to today)
    @Builder.Default
    private LocalDate effectiveDate = LocalDate.now();

    // "Pro-rata First Month".
    private LocalDate enrollmentDate;

    @NotNull(message = "Customer Segment is mandatory.")
    private String customerSegment;

    // "External Facility Counters".
    // Maps like: {"ATM_WITHDRAWAL_COUNT": 5, "POS_SPEND_TOTAL": 1200.50}
    private Map<String, Object> customAttributes;
}