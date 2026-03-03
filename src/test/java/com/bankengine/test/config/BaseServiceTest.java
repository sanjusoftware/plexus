package com.bankengine.test.config;

import com.bankengine.auth.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class BaseServiceTest {
    protected static final String TEST_BANK_ID = "TEST_BANK";

    @BeforeEach
    void setUpTenantContext() {
        TenantContextHolder.setBankId(TEST_BANK_ID);
        TenantContextHolder.setSystemBankId("SYSTEM");
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    protected void assertBigDecimal(String expected, BigDecimal actual, String message) {
        BigDecimal expectedScaled = new BigDecimal(expected).setScale(2, RoundingMode.HALF_UP);
        assertEquals(expectedScaled, actual, message);
    }
}
