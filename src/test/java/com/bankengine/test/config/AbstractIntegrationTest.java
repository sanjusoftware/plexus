package com.bankengine.test.config;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.pricing.TestTransactionHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Set;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    public static final String TEST_BANK_ID = "BANK_A";
    protected static final String OTHER_BANK_ID = "BANK_B_FOREIGN";

    @BeforeEach
    void setupBankContext() {
        // Essential for the test thread to access TenantRepository/Aspects
        TenantContextHolder.setBankId(TEST_BANK_ID);
    }

    @AfterEach
    void cleanupBankContext() {
        TenantContextHolder.clear();
    }

    /**
     * Helper for @BeforeAll or @BeforeEach to seed roles.
     * Uses a Map where Key = Role Name, Value = Set of Authorities.
     */
    protected static void seedBaseRoles(TestTransactionHelper txHelper, Map<String, Set<String>> roleMappings) {
        try {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            roleMappings.forEach(txHelper::getOrCreateRoleInDb);
            txHelper.flushAndClear();
        } finally {
            TenantContextHolder.clear();
        }
    }
}