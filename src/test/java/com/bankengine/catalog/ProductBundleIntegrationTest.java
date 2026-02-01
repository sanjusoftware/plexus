package com.bankengine.catalog;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.dto.ProductBundleRequest;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.BundleProductLinkRepository;
import com.bankengine.catalog.repository.ProductBundleRepository;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.CategoryConflictRule;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.pricing.TestTransactionHelper;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.repository.BundlePricingLinkRepository;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.bankengine.test.config.WithMockRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductBundleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestTransactionHelper txHelper;
    @Autowired
    private BankConfigurationRepository bankConfigRepository;
    @Autowired
    private ProductBundleRepository productBundleRepository;
    @Autowired
    private BundleProductLinkRepository bundleProductLinkRepository;
    @Autowired
    private BundlePricingLinkRepository bundlePricingLinkRepository;

    private Long retailProductId;
    private Long wealthProductId;
    private Long existingBundleId;

    private static final String BUNDLE_ADMIN = "BUNDLE_TEST_ADMIN";
    private static final String BUNDLE_UPDATER = "BUNDLE_UPDATER_ROLE";
    private static final String BUNDLE_ACTVATOR = "BUNDLE_ACTIVATOR_ROLE";

    @BeforeAll
    static void initSecurity(@Autowired TestTransactionHelper tx) {
        seedBaseRoles(tx, Map.of(
                BUNDLE_ADMIN, Set.of("catalog:bundle:create"),
                BUNDLE_UPDATER, Set.of("catalog:bundle:update"),
                BUNDLE_ACTVATOR, Set.of("catalog:bundle:activate", "catalog:bundle:delete")
        ));
    }

    @BeforeEach
    void setupData() {
        txHelper.doInTransaction(() -> {
            BankConfiguration config = bankConfigRepository.findByBankId(TEST_BANK_ID)
                    .orElseGet(() -> {
                        BankConfiguration newConfig = new BankConfiguration();
                        newConfig.setBankId(TEST_BANK_ID);
                        return newConfig;
                    });
            config.setCategoryConflictRules(new ArrayList<>(List.of(
                    new CategoryConflictRule("RETAIL", "WEALTH")
            )));
            bankConfigRepository.save(config);

            // 2. Setup Full Bundle
            // This method creates the bundle, product, and the link
            ProductBundle bundle = txHelper.setupFullBundleWithPricing(
                    "Integration Test Bundle",
                    "Retail Savings",
                    BigDecimal.valueOf(-10.00),
                    PriceValue.ValueType.DISCOUNT_ABSOLUTE,
                    ProductBundle.BundleStatus.DRAFT
            );

            existingBundleId = bundle.getId();

            // 3. REFRESH the bundle to ensure Hibernate populates the 'containedProducts' collection
            // Since we are inside a transaction, we need the DB to sync with the object
            txHelper.flushAndClear();

            // 4. Retrieve the bundle again to get the populated collection
            ProductBundle refreshedBundle = productBundleRepository.findById(existingBundleId).orElseThrow();

            // This will now succeed because the collection is no longer empty
            retailProductId = refreshedBundle.getContainedProducts().get(0).getProduct().getId();

            // Setup a second product for conflict testing
            ProductType type = txHelper.getOrCreateProductType("SAVINGS");
            wealthProductId = txHelper.getOrCreateProduct("Wealth Investment", type, "WEALTH").getId();
        });

        txHelper.flushAndClear();
    }

    @AfterEach
    void tearDown() {
        txHelper.doInTransaction(() -> {
            bundlePricingLinkRepository.deleteAllInBatch();
            bundleProductLinkRepository.deleteAllInBatch();
            productBundleRepository.deleteAllInBatch();
            TenantContextHolder.clear();
        });
    }

    @Test
    @WithMockRole(roles = BUNDLE_UPDATER)
    @DisplayName("PUT /id - Should create a new version of the bundle")
    void updateBundle_ShouldSucceed() throws Exception {
        ProductBundleRequest updateRequest = createBaseRequest("B-VER-2", "Updated Bundle Name");
        updateRequest.setItems(List.of(createItem(retailProductId, true)));

        mockMvc.perform(put("/api/v1/bundles/{id}", existingBundleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Verification: The old bundle should still exist but be ARCHIVED
        txHelper.doInTransaction(() -> {
            ProductBundle old = productBundleRepository.findById(existingBundleId)
                    .orElseThrow(() -> new AssertionError("Old bundle not found"));

            assertEquals(ProductBundle.BundleStatus.ARCHIVED, old.getStatus());
            assertNotNull(old.getExpiryDate());
        });
    }

    @Test
    @WithMockRole(roles = BUNDLE_ACTVATOR)
    @DisplayName("POST /id/activate - Should transition bundle to ACTIVE")
    void activateBundle_ShouldSucceed() throws Exception {
        mockMvc.perform(post("/api/v1/bundles/{id}/activate", existingBundleId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockRole(roles = BUNDLE_ADMIN)
    @DisplayName("POST /id/clone - Should create a copy with a new name")
    void cloneBundle_ShouldSucceed() throws Exception {
        mockMvc.perform(post("/api/v1/bundles/{id}/clone", existingBundleId)
                        .param("newName", "Cloned Premium Bundle"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockRole(roles = BUNDLE_ACTVATOR)
    @DisplayName("DELETE /id - Should soft-delete the bundle")
    void archiveBundle_ShouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/bundles/{id}", existingBundleId))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser // No roles assigned
    @DisplayName("Security - Should return 403 when user lacks authorities")
    void anyMethod_ShouldFail_WhenUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/bundles/{id}", existingBundleId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = BUNDLE_ADMIN)
    @DisplayName("POST - Should reject bundle creation when products have conflicting categories")
    void createBundle_ShouldFail_OnCategoryConflict() throws Exception {
        final Long[] localProducts = new Long[2];
        txHelper.doInTransaction(() -> {
            ProductType type = txHelper.getOrCreateProductType("SAVINGS");
            localProducts[0] = txHelper.getOrCreateProduct("Conflict Product A", type, "RETAIL").getId();
            localProducts[1] = txHelper.getOrCreateProduct("Conflict Product B", type, "WEALTH").getId();
        });

        // 2. Build the request using the local, unique products
        ProductBundleRequest request = createBaseRequest("B-CONFLICT-UNIQUE", "Conflict Bundle");
        request.setItems(List.of(
                createItem(localProducts[0], true),
                createItem(localProducts[1], false)
        ));

        mockMvc.perform(post("/api/v1/bundles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("Conflict")));
    }

    @Test
    @WithMockRole(roles = BUNDLE_ADMIN)
    @DisplayName("POST - Should reject bundle creation when multiple products are marked as main")
    void createBundle_ShouldFail_WhenMultipleMainProductsExist() throws Exception {
        ProductBundleRequest request = createBaseRequest("B-MULTI-MAIN", "Invalid Multi Main Bundle");
        request.setItems(List.of(
                createItem(retailProductId, true),
                createItem(wealthProductId, true)
        ));

        mockMvc.perform(post("/api/v1/bundles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("Main Account")));
    }

    private ProductBundleRequest createBaseRequest(String code, String name) {
        ProductBundleRequest request = new ProductBundleRequest();
        request.setCode(code);
        request.setName(name);
        request.setActivationDate(LocalDate.now().plusDays(7));
        request.setEligibilitySegment("RETAIL");
        return request;
    }

    private ProductBundleRequest.BundleItemRequest createItem(Long id, boolean main) {
        ProductBundleRequest.BundleItemRequest item = new ProductBundleRequest.BundleItemRequest();
        item.setProductId(id);
        item.setMainAccount(main);
        return item;
    }
}