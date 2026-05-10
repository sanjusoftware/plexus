package com.bankengine.pricing.service;

import java.util.Set;

public final class PricingAttributeKeys {

    private PricingAttributeKeys() {
    }

    public static final String CUSTOMER_SEGMENT = "CUSTOMER_SEGMENT";
    public static final String TRANSACTION_AMOUNT = "TRANSACTION_AMOUNT";
    public static final String EFFECTIVE_DATE = "EFFECTIVE_DATE";
    public static final String PRODUCT_ID = "PRODUCT_ID";
    public static final String PRODUCT_BUNDLE_ID = "PRODUCT_BUNDLE_ID";
    public static final String GROSS_TOTAL_AMOUNT = "GROSS_TOTAL_AMOUNT";
    public static final String BANK_ID = "BANK_ID";

    public static final Set<String> SYSTEM_KEYS = Set.of(
            CUSTOMER_SEGMENT,
            TRANSACTION_AMOUNT,
            EFFECTIVE_DATE,
            PRODUCT_ID,
            PRODUCT_BUNDLE_ID,
            GROSS_TOTAL_AMOUNT,
            BANK_ID
    );
}

