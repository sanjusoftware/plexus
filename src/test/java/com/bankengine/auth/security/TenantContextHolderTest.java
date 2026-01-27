package com.bankengine.auth.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextHolderTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void getBankId_ShouldThrowException_WhenNoContextAndNotSystemMode() {
        assertThrows(IllegalStateException.class, TenantContextHolder::getBankId);
    }

    @Test
    void getBankId_ShouldReturnNull_WhenInSystemMode() {
        TenantContextHolder.setSystemMode(true);
        // Should not throw exception, even if bankId is null
        assertNull(TenantContextHolder.getBankId());
    }

    @Test
    void getBankId_ShouldReturnId_WhenSet() {
        TenantContextHolder.setBankId("BANK_XYZ");
        assertEquals("BANK_XYZ", TenantContextHolder.getBankId());
    }
}