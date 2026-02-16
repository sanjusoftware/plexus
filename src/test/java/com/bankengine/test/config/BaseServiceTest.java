package com.bankengine.test.config;

import com.bankengine.auth.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

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
}
