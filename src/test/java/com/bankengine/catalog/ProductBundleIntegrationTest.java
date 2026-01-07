package com.bankengine.catalog;

import com.bankengine.catalog.dto.ProductBundleRequest;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.CategoryConflictRule;
import com.bankengine.config.repository.BankConfigurationRepository;
import com.bankengine.pricing.TestTransactionHelper;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class ProductBundleIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestTransactionHelper txHelper;
    @Autowired private BankConfigurationRepository bankConfigRepository;
    @Autowired private ProductTypeRepository productTypeRepository;
    @Autowired private ProductRepository productRepository;


    private Long retailProductId;
    private Long wealthProductId;

    @BeforeEach
    void setupData() {
        // 1. Setup real Conflict Rules in the DB
        BankConfiguration config = new BankConfiguration();
        config.setBankId(TEST_BANK_ID);
        config.setCategoryConflictRules(List.of(new CategoryConflictRule("RETAIL", "WEALTH")));
        bankConfigRepository.save(config);

        // 2. Setup real Products
        Long typeId = txHelper.doInTransaction(() -> {
            return productTypeRepository.findByName("Integration Test Type")
                    .map(ProductType::getId)
                    .orElseGet(() -> {
                        ProductType pt = new ProductType();
                        pt.setName("Integration Test Type");
                        pt.setBankId(TEST_BANK_ID);
                        return productTypeRepository.save(pt).getId();
                    });
        });

        // 3. Setup real Products (Find or Create to prevent further constraint violations)
        retailProductId = txHelper.doInTransaction(() ->
            productRepository.findByName("Retail Savings") // Assuming productRepository has findByName
                .map(Product::getId)
                .orElseGet(() -> txHelper.createProductInDb("Retail Savings", typeId, "RETAIL"))
        );

        wealthProductId = txHelper.doInTransaction(() ->
            productRepository.findByName("Wealth Investment")
                .map(Product::getId)
                .orElseGet(() -> txHelper.createProductInDb("Wealth Investment", typeId, "WEALTH"))
        );
    }

    @Test
    @WithMockUser(authorities = "catalog:bundle:create")
    @DisplayName("Should reject bundle creation when products have conflicting categories")
    void createBundle_ShouldFail_OnCategoryConflict() throws Exception {
        // Arrange
        ProductBundleRequest request = createBaseRequest("B-CONFLICT", "Conflict Bundle");
        request.setItems(List.of(
            createItem(retailProductId, true),
            createItem(wealthProductId, false)
        ));

        // Act & Assert
        mockMvc.perform(post("/api/v1/bundles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("Conflict")));
    }

    @Test
    @WithMockUser(authorities = "catalog:bundle:create")
    @DisplayName("Should reject bundle creation when multiple products are marked as main")
    void createBundle_ShouldFail_WhenMultipleMainProductsExist() throws Exception {
        // Arrange
        ProductBundleRequest request = createBaseRequest("B-MULTI-MAIN", "Invalid Multi Main Bundle");
        request.setItems(List.of(
            createItem(retailProductId, true),  // First Main
            createItem(wealthProductId, true)   // Second Main (Conflict!)
        ));

        // Act & Assert
        mockMvc.perform(post("/api/v1/bundles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()) // IllegalArgumentException maps to 400
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