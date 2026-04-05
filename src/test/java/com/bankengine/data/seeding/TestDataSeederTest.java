package com.bankengine.data.seeding;

import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.test.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles({"test", "dev"})
class TestDataSeederTest extends AbstractIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestDataSeeder testDataSeeder;

    @Test
    void testSeederProducedData() {
        com.bankengine.auth.security.TenantContextHolder.setSystemMode(true);
        try {
            // TestDataSeeder runs on startup because of 'dev' profile.
            // We just verify it did its job.
            long count = productRepository.count();
            assertTrue(count > 0, "Products should have been seeded");
        } finally {
            com.bankengine.auth.security.TenantContextHolder.clear();
        }
    }

    @Test
    void testSeederIdempotency() {
        com.bankengine.auth.security.TenantContextHolder.setSystemMode(true);
        com.bankengine.auth.security.TenantContextHolder.setBankId("GLOBAL-BANK-001");
        // Run the seeder again manually to verify it's idempotent
        testDataSeeder.run();

        com.bankengine.auth.security.TenantContextHolder.setSystemMode(true);
        com.bankengine.auth.security.TenantContextHolder.setBankId("GLOBAL-BANK-001");
        try {
            // If it reaches here without throwing a DataIntegrityViolationException, it's a good sign.
            long count = productRepository.count();
            assertTrue(count > 0, "Products should still exist after second run");
        } finally {
            com.bankengine.auth.security.TenantContextHolder.clear();
        }
    }
}
