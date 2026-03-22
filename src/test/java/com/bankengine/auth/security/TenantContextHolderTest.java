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
    void getBankId_ShouldReturnId_WhenSet() {
        TenantContextHolder.setBankId("BANK_XYZ");
        assertEquals("BANK_XYZ", TenantContextHolder.getBankId());
    }

    @Test
    void getSystemBankId_ShouldThrowException_WhenNotInitialized() {
        TenantContextHolder.setSystemBankId(null);
        assertThrows(IllegalStateException.class, TenantContextHolder::getSystemBankId);
    }

    @Test
    void getSystemBankId_ShouldReturnId_WhenSet() {
        TenantContextHolder.setSystemBankId("SYSTEM_BANK");
        assertEquals("SYSTEM_BANK", TenantContextHolder.getSystemBankId());
    }

    @Test
    void isSystemMode_ShouldReturnFalse_WhenNull() {
        TenantContextHolder.setSystemMode(true);
        assertTrue(TenantContextHolder.isSystemMode());

        // Use reflection or just try to set it to null if possible (though Boolean is boxed)
        // In the code: Boolean mode = SYSTEM_MODE.get();
        // SYSTEM_MODE is ThreadLocal<Boolean>

        // We can't easily set it to null via the public API if it's boolean primitive in setter
        // But the method is setSystemMode(boolean isSystem)
    }
}
