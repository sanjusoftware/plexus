package com.bankengine.test.config;

import com.bankengine.auth.security.BankContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for all integration tests requiring a valid Spring context, MockMvc, and multi-tenancy setup.
 * Handles the per-test lifecycle management of the ThreadLocal BankContextHolder.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    // Define the bank ID constant for all extending tests
    protected static final String TEST_BANK_ID = "BANK_A";
    protected static final String OTHER_BANK_ID = "BANK_B_FOREIGN";

    /**
     * Sets the thread-local Bank ID before each test runs.
     * This ensures the BankIdAuditorAware has a value for save/update operations.
     */
    @BeforeEach
    void setupBankContext() {
        BankContextHolder.setBankId(TEST_BANK_ID);
    }

    /**
     * Clears the thread-local Bank ID after each test.
     * CRITICAL for preventing context leakage between tests run on the same thread.
     */
    @AfterEach
    void cleanupBankContext() {
        BankContextHolder.clear();
    }
}