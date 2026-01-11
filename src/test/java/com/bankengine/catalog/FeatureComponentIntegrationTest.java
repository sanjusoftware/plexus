package com.bankengine.catalog;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.dto.FeatureComponentRequest;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.FeatureComponentRepository;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.pricing.TestTransactionHelper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class FeatureComponentIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FeatureComponentRepository featureComponentRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductTypeRepository productTypeRepository;
    @Autowired private ProductFeatureLinkRepository linkRepository;
    @Autowired private TestTransactionHelper txHelper;

    private static Product sharedProduct;

    public static final String ROLE_PREFIX = "FCIT_";
    private static final String ADMIN_ROLE = ROLE_PREFIX + "CATALOG_ADMIN";
    private static final String READER_ROLE = ROLE_PREFIX + "CATALOG_READER";
    private static final String UNAUTHORIZED_ROLE = ROLE_PREFIX + "UNAUTHORIZED_ROLE";

    @BeforeAll
    static void setupCommittedData(@Autowired TestTransactionHelper txHelperStatic,
                                   @Autowired ProductRepository productRepoStatic,
                                   @Autowired ProductTypeRepository productTypeRepoStatic,
                                   @Autowired FeatureComponentRepository featureRepoStatic,
                                   @Autowired ProductFeatureLinkRepository linkRepoStatic) {

        // 1. Seed Roles using Template Method
        seedBaseRoles(txHelperStatic, Map.of(
            ADMIN_ROLE, Set.of("catalog:feature:create", "catalog:feature:read", "catalog:feature:update", "catalog:feature:delete"),
            READER_ROLE, Set.of("catalog:feature:read"),
            UNAUTHORIZED_ROLE, Set.of("some:other:permission")
        ));

        // 2. Setup Shared Entities for Linking
        try {
            TenantContextHolder.setBankId(TEST_BANK_ID);

            txHelperStatic.doInTransaction(() -> {
                linkRepoStatic.deleteAllInBatch();
                featureRepoStatic.deleteAllInBatch();
                productRepoStatic.deleteAllInBatch();
                productTypeRepoStatic.deleteAllInBatch();

                ProductType type = new ProductType();
                type.setName("Test Type for Link");
                ProductType savedType = productTypeRepoStatic.save(type);

                Product product = new Product();
                product.setName("Link Test Product");
                product.setStatus("ACTIVE");
                product.setProductType(savedType);
                product.setCategory("RETAIL");
                sharedProduct = productRepoStatic.save(product);
            });
            txHelperStatic.flushAndClear();
        } finally {
            TenantContextHolder.clear();
        }
    }

    @AfterEach
    void tearDown() {
        txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            linkRepository.deleteAllInBatch();
            featureComponentRepository.deleteAllInBatch();
        });
        txHelper.flushAndClear();
    }

    private FeatureComponentRequest getCreateDto(String name) {
        FeatureComponentRequest dto = new FeatureComponentRequest();
        dto.setName(name);
        dto.setDataType("STRING");
        return dto;
    }

    private FeatureComponent createFeatureComponentInDb(String name) {
        return txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            return featureComponentRepository.findByName(name)
                    .orElseGet(() -> {
                        FeatureComponent component = new FeatureComponent();
                        component.setName(name);
                        component.setDataType(FeatureComponent.DataType.STRING);
                        return featureComponentRepository.save(component);
                    });
        });
    }

    // --- 1. CREATE TESTS ---

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn403WhenCreatingFeatureWithoutPermission() throws Exception {
        mockMvc.perform(post("/api/v1/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(getCreateDto("ForbiddenFeature"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldCreateFeatureAndReturn201() throws Exception {
        mockMvc.perform(post("/api/v1/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(getCreateDto("PremiumSupport"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("PremiumSupport")))
                .andExpect(jsonPath("$.dataType", is("STRING")))
                .andExpect(jsonPath("$.id").isNumber());
        // Verify DB Tenancy & Auditing
        txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            FeatureComponent fc = featureComponentRepository.findByName("PremiumSupport").orElseThrow();
            assertThat(fc.getBankId()).isEqualTo(TEST_BANK_ID);
            assertThat(fc.getCreatedBy()).isNotEqualTo("SYSTEM"); // Should be mock user
        });
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn400OnCreateWithInvalidDataType() throws Exception {
        FeatureComponentRequest dto = getCreateDto("BadTypeFeature");
        dto.setDataType("XYZ");

        mockMvc.perform(post("/api/v1/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Invalid data type provided: XYZ")));
    }

    // --- 2. RETRIEVE TESTS ---

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void shouldReturn403WhenReadingFeatureWithoutPermission() throws Exception {
        FeatureComponent savedComponent = createFeatureComponentInDb("ForbiddenFeature");
        mockMvc.perform(get("/api/v1/features/{id}", savedComponent.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn200AndFeatureById() throws Exception {
        FeatureComponent savedComponent = createFeatureComponentInDb("ATMWithdrawals");

        mockMvc.perform(get("/api/v1/features/{id}", savedComponent.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("ATMWithdrawals")))
                .andExpect(jsonPath("$.id", is(savedComponent.getId().intValue())));
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn404WhenGettingNonExistentFeature() throws Exception {
        mockMvc.perform(get("/api/v1/features/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    // --- 3. UPDATE TESTS ---

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn403WhenUpdatingFeatureWithoutPermission() throws Exception {
        FeatureComponent savedComponent = createFeatureComponentInDb("ForbiddenFeature");
        FeatureComponentRequest updateDto = getCreateDto("NewName");

        mockMvc.perform(put("/api/v1/features/{id}", savedComponent.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldUpdateFeatureAndReturn200() throws Exception {
        FeatureComponent savedComponent = createFeatureComponentInDb("OldName");
        FeatureComponentRequest updateDto = new FeatureComponentRequest();
        updateDto.setName("NewName");
        updateDto.setDataType("BOOLEAN");

        mockMvc.perform(put("/api/v1/features/{id}", savedComponent.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("NewName")))
                .andExpect(jsonPath("$.dataType", is("BOOLEAN")))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn404OnUpdateNonExistentFeature() throws Exception {
        mockMvc.perform(put("/api/v1/features/99999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(getCreateDto("Test"))))
                .andExpect(status().isNotFound());
    }

    // --- 4. DELETE TESTS ---

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn403WhenDeletingFeatureWithoutPermission() throws Exception {
        FeatureComponent savedComponent = createFeatureComponentInDb("ForbiddenFeature");
        mockMvc.perform(delete("/api/v1/features/{id}", savedComponent.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldDeleteFeatureAndReturn204() throws Exception {
        FeatureComponent savedComponent = createFeatureComponentInDb("DeletableFeature");
        Long idToDelete = savedComponent.getId();

        mockMvc.perform(delete("/api/v1/features/{id}", idToDelete))
                .andExpect(status().isNoContent());

        // Assert via API
        mockMvc.perform(get("/api/v1/features/{id}", idToDelete))
                .andExpect(status().isNotFound());

        // Assert via DB
        txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            assertThat(featureComponentRepository.findById(idToDelete)).isEmpty();
        });
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn409WhenDeletingLinkedFeature() throws Exception {
        FeatureComponent linkedComponent = createFeatureComponentInDb("LinkedFeature");

        txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            ProductFeatureLink link = new ProductFeatureLink();
            link.setFeatureComponent(linkedComponent);
            link.setProduct(sharedProduct);
            link.setFeatureValue("Default Value");
            linkRepository.save(link);
        });

        mockMvc.perform(delete("/api/v1/features/{id}", linkedComponent.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("Cannot delete Feature Component ID")));
    }
}