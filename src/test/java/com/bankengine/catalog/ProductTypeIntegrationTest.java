package com.bankengine.catalog;

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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private static final String ADMIN_ROLE = ROLE_PREFIX + "PRODUCT_TYPE_ADMIN";
    private static final String UNAUTHORIZED_ROLE = ROLE_PREFIX + "UNAUTHORIZED";

    @BeforeAll
    static void setupCommittedRoles(@Autowired TestTransactionHelper txHelperStatic) {
        seedBaseRoles(txHelperStatic, Map.of(
            CREATOR_ROLE, Set.of("catalog:product-type:create", "catalog:product-type:read"),
            READER_ROLE, Set.of("catalog:product-type:read"),
            ADMIN_ROLE, Set.of(
                "catalog:product-type:create",
                "catalog:product-type:read",
                "catalog:product-type:update",
                "catalog:product-type:activate",
                "catalog:product-type:archive",
                "catalog:product-type:delete"
            ),
            UNAUTHORIZED_ROLE, Set.of("some:other:permission")
        ));
    }

    @AfterEach
    void cleanUp() {
        txHelper.doInTransaction(() -> {
            productFeatureLinkRepository.deleteAllInBatch();
            productPricingLinkRepository.deleteAllInBatch();
            productRepository.deleteAllInBatch();
            productTypeRepository.deleteAllInBatch();
        });
    }

    private ProductType createAndSaveProductType(String name, String code) {
        return txHelper.doInTransaction(() -> {
            ProductType type = new ProductType();
            type.setName(name);
            type.setCode(code);
            type.setBankId(TEST_BANK_ID);
            return productTypeRepository.save(type);
        });
    }

    // =================================================================
    // GET /api/v1/product-types
    // =================================================================

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn200AndAllProductTypes() throws Exception {
        createAndSaveProductType("Current and Savings Account", "CASA");
        createAndSaveProductType("Credit Card", "CC");

        mockMvc.perform(get(API_URL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.code == 'CASA')].name").value("Current and Savings Account"))
                .andExpect(jsonPath("$[?(@.code == 'CC')].name").value("Credit Card"))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].updatedAt").exists());
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
        requestDto.setCode("FD");

        mockMvc.perform(postWithCsrf(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Fixed Deposit")))
                .andExpect(jsonPath("$.code", is("FD")))
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @WithMockRole(roles = {CREATOR_ROLE})
    void shouldSanitizeProductTypeCodeOnCreate() throws Exception {
        ProductTypeDto requestDto = new ProductTypeDto();
        requestDto.setName("Current Account");
        requestDto.setCode("CASA Account");

        mockMvc.perform(postWithCsrf(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is("CASA_ACCOUNT")));
    }

    @Test
    @WithMockRole(roles = {CREATOR_ROLE})
    void shouldAllowDuplicateProductTypeNameWhenCodeDiffers() throws Exception {
        ProductTypeDto first = new ProductTypeDto();
        first.setName("Current Account");
        first.setCode("CASA_A");

        ProductTypeDto second = new ProductTypeDto();
        second.setName("Current Account");
        second.setCode("CASA_B");

        mockMvc.perform(postWithCsrf(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        mockMvc.perform(postWithCsrf(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is("CASA_B")));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldActivateProductType() throws Exception {
        ProductType pt = createAndSaveProductType("Inactive Type", "INACT");

        mockMvc.perform(postWithCsrf(API_URL + "/" + pt.getId() + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldArchiveProductType() throws Exception {
        ProductType pt = createAndSaveProductType("Active Type", "ACT");
        // Manually set to ACTIVE first
        txHelper.doInTransaction(() -> {
            ProductType entity = productTypeRepository.findById(pt.getId()).get();
            entity.setStatus(com.bankengine.common.model.VersionableEntity.EntityStatus.ACTIVE);
            productTypeRepository.save(entity);
            return null;
        });

        mockMvc.perform(postWithCsrf(API_URL + "/" + pt.getId() + "/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ARCHIVED")));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldUpdateDraftProductType() throws Exception {
        ProductType pt = createAndSaveProductType("Old Name", "OLD_CODE");
        ProductTypeDto updateDto = new ProductTypeDto();
        updateDto.setName("Updated Name");
        updateDto.setCode("NEW CODE");

        mockMvc.perform(putWithCsrf(API_URL + "/" + pt.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Name")))
                .andExpect(jsonPath("$.code", is("NEW_CODE")))
                .andExpect(jsonPath("$.status", is("DRAFT")));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldArchiveOnDeleteWhenProductTypeIsActive() throws Exception {
        ProductType pt = createAndSaveProductType("Active Type", "ACTIVE_DEL");
        txHelper.doInTransaction(() -> {
            ProductType entity = productTypeRepository.findById(pt.getId()).orElseThrow();
            entity.setStatus(com.bankengine.common.model.VersionableEntity.EntityStatus.ACTIVE);
            productTypeRepository.save(entity);
            return null;
        });

        mockMvc.perform(deleteWithCsrf(API_URL + "/" + pt.getId()))
                .andExpect(status().isNoContent());

        txHelper.doInTransaction(() -> {
            ProductType saved = productTypeRepository.findById(pt.getId()).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(
                    com.bankengine.common.model.VersionableEntity.EntityStatus.ARCHIVED,
                    saved.getStatus());
            return null;
        });
    }

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void shouldReturn403WhenCreatingWithoutPermission() throws Exception {
        ProductTypeDto requestDto = new ProductTypeDto();
        requestDto.setName("Forbidden Type");
        requestDto.setCode("FT");

        mockMvc.perform(postWithCsrf(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = {CREATOR_ROLE})
    void shouldReturn400WhenNameIsMissing() throws Exception {
        ProductTypeDto requestDto = new ProductTypeDto();
        requestDto.setName(null);

        mockMvc.perform(postWithCsrf(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].reason", hasItem("Product Type name is required.")));
    }

    @Test
    @WithMockRole(roles = {CREATOR_ROLE})
    void shouldReturn400WhenNameIsTooShort() throws Exception {
        ProductTypeDto requestDto = new ProductTypeDto();
        requestDto.setName("A");

        mockMvc.perform(postWithCsrf(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].reason", hasItem("Name must be between 3 and 100 characters.")));
    }


    @Test
    @WithMockRole(roles = {CREATOR_ROLE})
    void shouldReturn409ConflictWhenCreatingDuplicateCode() throws Exception {
        createAndSaveProductType("Checking Account 1", "CH");

        ProductTypeDto requestDto = new ProductTypeDto();
        requestDto.setName("Checking Account 2");
        requestDto.setCode("CH");

        mockMvc.perform(postWithCsrf(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }
}