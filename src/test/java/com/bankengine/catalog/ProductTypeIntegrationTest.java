package com.bankengine.catalog;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.dto.ProductTypeDto;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.pricing.TestTransactionHelper;
import com.bankengine.pricing.repository.ProductPricingLinkRepository;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.bankengine.test.config.WithMockRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductTypeIntegrationTest extends AbstractIntegrationTest {

    private static final String API_URL = "/api/v1/product-types";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductTypeRepository productTypeRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductFeatureLinkRepository productFeatureLinkRepository;
    @Autowired private ProductPricingLinkRepository productPricingLinkRepository;
    @Autowired private TestTransactionHelper txHelper;

    // --- Role Constants ---
    public static final String ROLE_PREFIX = "PTIT_";
    private static final String CREATOR_ROLE = ROLE_PREFIX + "PRODUCT_TYPE_CREATOR";
    private static final String READER_ROLE = ROLE_PREFIX + "PRODUCT_TYPE_READER";
    private static final String UNAUTHORIZED_ROLE = ROLE_PREFIX + "UNAUTHORIZED";

    @BeforeAll
    static void setupCommittedRoles(@Autowired TestTransactionHelper txHelperStatic) {
        seedBaseRoles(txHelperStatic, Map.of(
            CREATOR_ROLE, Set.of("catalog:product-type:create", "catalog:product-type:read"),
            READER_ROLE, Set.of("catalog:product-type:read"),
            UNAUTHORIZED_ROLE, Set.of("some:other:permission")
        ));
    }

    @AfterEach
    void cleanUp() {
        txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            productFeatureLinkRepository.deleteAllInBatch();
            productPricingLinkRepository.deleteAllInBatch();
            productRepository.deleteAllInBatch();
            productTypeRepository.deleteAllInBatch();
        });
    }

    private ProductType createAndSaveProductType(String name) {
        return txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            ProductType type = new ProductType();
            type.setName(name);
            return productTypeRepository.save(type);
        });
    }

    // =================================================================
    // GET /api/v1/product-types
    // =================================================================

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn200AndAllProductTypes() throws Exception {
        createAndSaveProductType("CASA");
        createAndSaveProductType("Credit Card");

        mockMvc.perform(get(API_URL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("CASA")))
                .andExpect(jsonPath("$[1].name", is("Credit Card")));
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn200AndEmptyListWhenNoProductTypesExist() throws Exception {
        mockMvc.perform(get(API_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void shouldReturn403WhenReadingWithoutPermission() throws Exception {
        mockMvc.perform(get(API_URL))
                .andExpect(status().isForbidden());
    }

    // =================================================================
    // POST /api/v1/product-types
    // =================================================================

    @Test
    @WithMockRole(roles = {CREATOR_ROLE})
    void shouldReturn201AndCreateProductTypeSuccessfully() throws Exception {
        ProductTypeDto requestDto = new ProductTypeDto();
        requestDto.setName("Fixed Deposit");

        mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Fixed Deposit")))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void shouldReturn403WhenCreatingWithoutPermission() throws Exception {
        ProductTypeDto requestDto = new ProductTypeDto();
        requestDto.setName("Forbidden Type");

        mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = {CREATOR_ROLE})
    void shouldReturn400WhenNameIsMissing() throws Exception {
        ProductTypeDto requestDto = new ProductTypeDto();
        requestDto.setName(null);

        mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").value("Product Type name is required."));
    }

    @Test
    @WithMockRole(roles = {CREATOR_ROLE})
    void shouldReturn400WhenNameIsTooShort() throws Exception {
        ProductTypeDto requestDto = new ProductTypeDto();
        requestDto.setName("A");

        mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").value("Name must be between 3 and 100 characters."));
    }

    @Test
    @WithMockRole(roles = {CREATOR_ROLE})
    void shouldReturn409ConflictWhenCreatingDuplicateName() throws Exception {
        createAndSaveProductType("Checking Account");

        ProductTypeDto requestDto = new ProductTypeDto();
        requestDto.setName("Checking Account");

        mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }
}