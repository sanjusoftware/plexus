package com.bankengine.auth.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextHolderTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @BeforeEach
    void setUp() { TenantContextHolder.clear(); }

    @Test
    void getBankId_ShouldThrowException_WhenNoContextAndNotSystemMode() {
        assertThrows(IllegalStateException.class, TenantContextHolder::getBankId);
    }

    @Test
    void getBankId_ShouldReturnNull_WhenInSystemMode() {
        TenantContextHolder.setSystemMode(true);
        assertNull(TenantContextHolder.getBankId());
    }

    @Test
    void getBankId_ShouldReturnId_WhenSet() {
        TenantContextHolder.setBankId("BANK_XYZ");
        assertEquals("BANK_XYZ", TenantContextHolder.getBankId());
    }

    @Test
    void getSystemBankId_ShouldThrowException_WhenNotInitialized() {
        // systemBankId is static and might have been initialized by other tests
        // But for coverage of the null check:
        TenantContextHolder.setSystemBankId(null);
        assertThrows(IllegalStateException.class, TenantContextHolder::getSystemBankId);
    }

    @Test
    void getSystemBankId_ShouldReturnId_WhenSet() {
        TenantContextHolder.setSystemBankId("SYSTEM_BANK");
        assertEquals("SYSTEM_BANK", TenantContextHolder.getSystemBankId());
    }
}