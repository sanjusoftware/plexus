package com.bankengine.pricing.dto;

import com.bankengine.pricing.model.PriceValue.ValueType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Value
@Builder
public class PriceValueResponseDto {
    private Long id;
    private BigDecimal priceAmount;
    private ValueType valueType;
    private String currency;

    /** Code of the pricing component (e.g., "ANNUAL_FEE", "INTEREST_RATE"). */
    private String pricingComponentCode;

    /** Context or purpose of the specific price (e.g., "CORE_RATE", "BUNDLE_DISCOUNT"). */
    private String context;

    /** Indicates how the price was derived: FIXED_VALUE or RULES_ENGINE. */
    private String sourceType;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}