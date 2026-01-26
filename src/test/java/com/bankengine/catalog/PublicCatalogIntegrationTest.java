package com.bankengine.catalog;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.repository.BundleProductLinkRepository;
import com.bankengine.catalog.repository.ProductBundleRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.pricing.TestTransactionHelper;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.repository.BundlePricingLinkRepository;
import com.bankengine.pricing.repository.ProductPricingLinkRepository;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.bankengine.test.config.WithMockRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicCatalogIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProductRepository productRepository;
    @Autowired private BundlePricingLinkRepository bundlePricingLinkRepository;
    @Autowired private BundleProductLinkRepository bundleProductLinkRepository;
    @Autowired private ProductBundleRepository productBundleRepository;
    @Autowired private ProductPricingLinkRepository productPricingLinkRepository;
    @Autowired private TestTransactionHelper txHelper;

    @BeforeAll
    static void setup(@Autowired TestTransactionHelper txHelperStatic) {
        seedBaseRoles(txHelperStatic, Map.of(
                "CUSTOMER", Set.of("catalog:read", "catalog:recommend"),
                "STRANGER", Set.of("catalog:read")
        ));
    }

    @AfterEach
    void clean() {
        txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            // 1. Delete all Pricing Links (Join tables/Dependencies)
            bundlePricingLinkRepository.deleteAllInBatch();
            productPricingLinkRepository.deleteAllInBatch(); // Added this

            // 2. Delete Product-to-Bundle links
            bundleProductLinkRepository.deleteAllInBatch();

            // 3. Delete Parents
            productBundleRepository.deleteAllInBatch();
            productRepository.deleteAllInBatch();
        });
    }

    @Test
    @DisplayName("Public Access - Browse products should work without login")
    void testPublicBrowse() throws Exception {
        mockMvc.perform(get("/api/v1/public/catalog/products"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Security - Recommendations should return 403 for anonymous")
    void testRecommendationSecurity() throws Exception {
        mockMvc.perform(get("/api/v1/public/catalog/products/recommended")
                        .param("customerSegment", "RETAIL"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockRole(roles = {"STRANGER"})
    @DisplayName("Security - Recommendations should return 403 for user with wrong role")
    void testRecommendationForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/public/catalog/products/recommended").param("customerSegment", "RETAIL"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = {"CUSTOMER"})
    @DisplayName("Auth Access - Recommendations should work for logged-in user")
    void testRecommendationAuth() throws Exception {
        mockMvc.perform(get("/api/v1/public/catalog/products/recommended")
                        .param("customerSegment", "RETAIL"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Public Bundle Display - Should show net total and list of benefits")
    void testPublicBundleDisplay() throws Exception {
        // ARRANGE: Use the helper to build a $0 standalone product bundled with a 20% discount
        // Note: Since individual product pricing isn't setup here, grossTotal is 0,
        // so we use a FEE_ABSOLUTE of 10.00 and a DISCOUNT_ABSOLUTE of 2.00 to test the math.

        Long bundleId = txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);

            Long bId = txHelper.setupFullBundleWithPricing(
                    "Welcome Bundle",
                    "Basic Savings",
                    new BigDecimal("-2.00"), // Negative for discount
                    PriceValue.ValueType.DISCOUNT_ABSOLUTE
            );

            // Let's add a fixed fee to the bundle so we have a base price to test
            PricingComponent baseFee = txHelper.createPricingComponentInDb("Monthly Package Fee");
            txHelper.linkBundleToPricingComponent(bId, baseFee.getId(), new BigDecimal("10.00"), PriceValue.ValueType.FEE_ABSOLUTE);

            return bId;
        });

        // ACT & ASSERT
        mockMvc.perform(get("/api/v1/public/catalog/bundles/" + bundleId)
                        .param("segment", "RETAIL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricing.totalMonthlyFee", is(8.00))) // 10.00 - 2.00
                .andExpect(jsonPath("$.pricing.totalSavings", is(2.00)))
                .andExpect(jsonPath("$.name", is("Welcome Bundle")));
    }
}