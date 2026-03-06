package com.bankengine.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantSecurityInitializerTest {

    @Test
    void init_ShouldSetSystemBankId_WhenValid() {
        TenantSecurityInitializer initializer = new TenantSecurityInitializer();
        ReflectionTestUtils.setField(initializer, "systemBankId", "SYSTEM");

        initializer.init();

        assertEquals("SYSTEM", TenantContextHolder.getSystemBankId());
    }

    @Test
    void init_ShouldThrowException_WhenSystemBankIdIsNull() {
        TenantSecurityInitializer initializer = new TenantSecurityInitializer();
        ReflectionTestUtils.setField(initializer, "systemBankId", null);

        assertThrows(IllegalStateException.class, initializer::init);
    }

    @Test
    void init_ShouldThrowException_WhenSystemBankIdIsBlank() {
        TenantSecurityInitializer initializer = new TenantSecurityInitializer();
        ReflectionTestUtils.setField(initializer, "systemBankId", "  ");

        assertThrows(IllegalStateException.class, initializer::init);
    }
}
