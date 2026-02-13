package com.bankengine.pricing.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public enum PricingDataType {
    STRING("java.lang.String", true),
    DECIMAL("java.math.BigDecimal", false),
    LONG("java.lang.Long", false),
    BOOLEAN("java.lang.Boolean", false),
    DATE("java.time.LocalDate", true);

    private final String fqn;
    private final boolean quoted;

    public static PricingDataType fromString(String value) {
        if (value == null) {
            throw new IllegalStateException("Data type cannot be null");
        }

        String lookup = value.toUpperCase().trim();
        if ("INTEGER".equals(lookup)) {
            value = "LONG";
        }

        try {
            return PricingDataType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Unsupported pricing data type: " + value);
        }
    }
}