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

    @Test
    void testSeederProducedData() {
        // TestDataSeeder runs on startup because of 'dev' profile.
        // We just verify it did its job.
        long count = productRepository.count();
        assertTrue(count > 0, "Products should have been seeded");
    }
}
