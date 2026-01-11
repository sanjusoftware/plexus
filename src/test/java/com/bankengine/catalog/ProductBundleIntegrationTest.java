package com.bankengine.catalog;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.dto.ProductBundleRequest;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.CategoryConflictRule;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.pricing.TestTransactionHelper;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.bankengine.test.config.WithMockRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductBundleIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestTransactionHelper txHelper;
    @Autowired private BankConfigurationRepository bankConfigRepository;

    private Long retailProductId;
    private Long wealthProductId;

    private static final String BUNDLE_ADMIN = "BUNDLE_TEST_ADMIN";

    @BeforeAll
    static void initSecurity(@Autowired TestTransactionHelper tx) {
        seedBaseRoles(tx, Map.of(
            BUNDLE_ADMIN, Set.of("catalog:bundle:create")
        ));
    }

    @BeforeEach
    void setupData() {
        txHelper.doInTransaction(() -> {
            // Re-assert context for the transaction thread
            TenantContextHolder.setBankId(TEST_BANK_ID);

            // 1. Setup Bank Configuration with Conflict Rules
            bankConfigRepository.findByBankId(TEST_BANK_ID)
                    .ifPresentOrElse(
                            existing -> {
                                existing.setCategoryConflictRules(new ArrayList<>(List.of(
                                        new CategoryConflictRule("RETAIL", "WEALTH")
                                )));
                                bankConfigRepository.save(existing);
                            },
                            () -> {
                                BankConfiguration config = new BankConfiguration();
                                config.setBankId(TEST_BANK_ID);
                                config.setCategoryConflictRules(new ArrayList<>(List.of(
                                        new CategoryConflictRule("RETAIL", "WEALTH")
                                )));
                                bankConfigRepository.save(config);
                            }
                    );

            // 2. Setup Products
            ProductType type = txHelper.getOrCreateProductType("Integration Test Type");
            retailProductId = txHelper.getOrCreateProduct("Retail Savings", type, "RETAIL").getId();
            wealthProductId = txHelper.getOrCreateProduct("Wealth Investment", type, "WEALTH").getId();
        });

        txHelper.flushAndClear();
    }

    @Test
    @WithMockRole(roles = BUNDLE_ADMIN)
    @DisplayName("Should reject bundle creation when products have conflicting categories")
    void createBundle_ShouldFail_OnCategoryConflict() throws Exception {
        ProductBundleRequest request = createBaseRequest("B-CONFLICT", "Conflict Bundle");
        request.setItems(List.of(
                createItem(retailProductId, true),
                createItem(wealthProductId, false)
        ));

        mockMvc.perform(post("/api/v1/bundles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("Conflict")));
    }

    @Test
    @WithMockRole(roles = BUNDLE_ADMIN)
    @DisplayName("Should reject bundle creation when multiple products are marked as main")
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

    // --- HELPER METHODS ---

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