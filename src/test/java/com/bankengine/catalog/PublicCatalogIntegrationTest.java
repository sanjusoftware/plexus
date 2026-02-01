package com.bankengine.catalog;

import com.bankengine.catalog.model.ProductBundle;
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
            bundlePricingLinkRepository.deleteAllInBatch();
            productPricingLinkRepository.deleteAllInBatch();
            bundleProductLinkRepository.deleteAllInBatch();
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
        mockMvc.perform(get("/api/v1/public/catalog/products/recommended")
                        .param("customerSegment", "RETAIL"))
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
    @WithMockRole(roles = {"STRANGER"})
    @DisplayName("Public Bundle Display - Should show net total and list of benefits")
    void testPublicBundleDisplay() throws Exception {
        Long bundleId = txHelper.doInTransaction(() -> {
            ProductBundle bundle = txHelper.setupFullBundleWithPricing(
                    "Welcome Bundle",
                    "Basic Savings",
                    new BigDecimal("-2.00"),
                    PriceValue.ValueType.DISCOUNT_ABSOLUTE,
                    ProductBundle.BundleStatus.ACTIVE
            );

            PricingComponent bundleFee = txHelper.createPricingComponentInDb("Monthly Package Fee");
            txHelper.linkBundleToPricingComponent(bundle.getId(), bundleFee.getId(), new BigDecimal("10.00"));
            txHelper.flushAndClear();

            return bundle.getId();
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