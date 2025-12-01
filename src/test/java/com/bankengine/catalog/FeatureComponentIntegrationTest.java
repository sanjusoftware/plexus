package com.bankengine.catalog;

import com.bankengine.auth.config.test.WithMockRole;
import com.bankengine.auth.security.BankContextHolder;
import com.bankengine.catalog.dto.CreateFeatureComponentRequestDto;
import com.bankengine.catalog.dto.UpdateFeatureComponentRequestDto;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class FeatureComponentIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FeatureComponentRepository featureComponentRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductTypeRepository productTypeRepository;

    @Autowired
    private ProductFeatureLinkRepository linkRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TestTransactionHelper txHelper;

    // Shared data entities - Must be static for use in @BeforeAll
    private static Product sharedProduct;
    private static Long EXISTING_PRODUCT_TYPE_ID;

    // --- Role Constants ---
    public static final String ROLE_PREFIX = "FCIT_";
    private static final String ADMIN_ROLE = ROLE_PREFIX + "CATALOG_ADMIN";
    private static final String READER_ROLE = ROLE_PREFIX + "CATALOG_READER";
    private static final String UNAUTHORIZED_ROLE = ROLE_PREFIX + "UNAUTHORIZED_ROLE";


    // =================================================================
    // SETUP AND TEARDOWN (Committed Data Lifecycle)
    // =================================================================

    /**
     * Set up all required roles, permissions, and shared committed entities once before all tests run.
     */
    @BeforeAll
    static void setupCommittedData(@Autowired TestTransactionHelper txHelperStatic,
                                   @Autowired ProductRepository productRepoStatic,
                                   @Autowired ProductTypeRepository productTypeRepoStatic,
                                   @Autowired FeatureComponentRepository featureRepoStatic,
                                   @Autowired ProductFeatureLinkRepository linkRepoStatic) {

        // Set the context for the static @BeforeAll execution thread
        BankContextHolder.setBankId(TEST_BANK_ID);

        try {
            // 1. GLOBAL CLEANUP (Aggressive, ensures a clean start)
            txHelperStatic.doInTransaction(() -> {
                linkRepoStatic.deleteAllInBatch();
                featureRepoStatic.deleteAllInBatch();
                productRepoStatic.deleteAllInBatch();
                productTypeRepoStatic.deleteAllInBatch();
            });
            txHelperStatic.flushAndClear();

            // 2. Setup Roles (Committed Transaction)
            Set<String> adminAuths = Set.of(
                    "catalog:feature:create", "catalog:feature:read",
                    "catalog:feature:update", "catalog:feature:delete"
            );
            Set<String> readerAuths = Set.of("catalog:feature:read");
            Set<String> unauthorizedAuths = Set.of("some:other:permission");


            txHelperStatic.createRoleInDb(ADMIN_ROLE, adminAuths);
            txHelperStatic.createRoleInDb(READER_ROLE, readerAuths);
            txHelperStatic.createRoleInDb(UNAUTHORIZED_ROLE, unauthorizedAuths);

            // 3. Setup Committed Dependencies (ProductType and Product for Linking)
            txHelperStatic.doInTransaction(() -> {
                // a. Find/Create a minimal ProductType dependency
                ProductType sharedProductType = productTypeRepoStatic.findByName("Test Type for Link")
                        .orElseGet(() -> {
                            ProductType newType = new ProductType();
                            newType.setName("Test Type for Link");
                            // bank_id is injected here by AuditorAware
                            return productTypeRepoStatic.save(newType);
                        });

                EXISTING_PRODUCT_TYPE_ID = sharedProductType.getId();

                // b. Find/Create a minimal Product entity to link to
                sharedProduct = productRepoStatic.findByName("Link Test Product")
                        .orElseGet(() -> {
                            Product product = new Product();
                            product.setName("Link Test Product");
                            product.setStatus("ACTIVE");
                            product.setProductType(sharedProductType);
                            // bank_id is injected here by AuditorAware
                            return productRepoStatic.save(product);
                        });
            });

            txHelperStatic.flushAndClear();
        } finally {
            BankContextHolder.clear();
        }
    }

    /**
     * Clean up ALL committed test data (created via committed helper or API) after each test.
     * NOTE: The BankContextHolder cleanup is handled by the parent class's @AfterEach.
     */
    @AfterEach
    void tearDown() {
        txHelper.doInTransaction(() -> {
            linkRepository.deleteAllInBatch();
            featureComponentRepository.deleteAllInBatch();
        });
        txHelper.flushAndClear();
    }


    // =================================================================
    // HELPER METHODS
    // =================================================================

    // Helper method to create a valid DTO for POST/PUT requests
    private CreateFeatureComponentRequestDto getCreateDto(String name) {
        CreateFeatureComponentRequestDto dto = new CreateFeatureComponentRequestDto();
        dto.setName(name);
        dto.setDataType("STRING");
        return dto;
    }

    // Helper method to create an entity directly in the DB (data is committed)
    private FeatureComponent createFeatureComponentInDb(String name) {
        return txHelper.doInTransaction(() -> {
            return featureComponentRepository.findByName(name)
                    .orElseGet(() -> {
                        FeatureComponent component = new FeatureComponent();
                        component.setName(name);
                        component.setDataType(FeatureComponent.DataType.STRING);
                        return featureComponentRepository.save(component);
                    });
        });
    }

    // =================================================================
    // 1. CREATE (POST) TESTS
    // =================================================================

    @Test
    // User has READER role, which lacks 'create' permission
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
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn400OnCreateWithInvalidDataType() throws Exception {
        CreateFeatureComponentRequestDto dto = getCreateDto("BadTypeFeature");
        dto.setDataType("XYZ"); // Invalid data type

        mockMvc.perform(post("/api/v1/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Invalid data type provided: XYZ")));
    }

    // =================================================================
    // 2. RETRIEVE (GET) TESTS
    // =================================================================

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void shouldReturn403WhenReadingFeatureWithoutPermission() throws Exception {
        // The component must be created and committed before the test runs
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

    // =================================================================
    // 3. UPDATE (PUT) TESTS
    // =================================================================

    @Test
    @WithMockRole(roles = {READER_ROLE}) // <-- Lacks 'update' permission
    void shouldReturn403WhenUpdatingFeatureWithoutPermission() throws Exception {
        FeatureComponent savedComponent = createFeatureComponentInDb("ForbiddenFeature");
        UpdateFeatureComponentRequestDto updateDto = new UpdateFeatureComponentRequestDto();
        updateDto.setName("NewName");
        updateDto.setDataType("BOOLEAN");

        mockMvc.perform(put("/api/v1/features/{id}", savedComponent.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldUpdateFeatureAndReturn200() throws Exception {
        FeatureComponent savedComponent = createFeatureComponentInDb("OldName");
        UpdateFeatureComponentRequestDto updateDto = new UpdateFeatureComponentRequestDto();
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
        UpdateFeatureComponentRequestDto updateDto = new UpdateFeatureComponentRequestDto();
        updateDto.setName("Test");
        updateDto.setDataType("STRING");

        mockMvc.perform(put("/api/v1/features/99999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isNotFound());
    }

    // =================================================================
    // 4. DELETE (DELETE) TESTS - CRITICAL DEPENDENCY CHECKS
    // =================================================================

    @Test
    @WithMockRole(roles = {READER_ROLE}) // <-- Lacks 'delete' permission
    void shouldReturn403WhenDeletingFeatureWithoutPermission() throws Exception {
        FeatureComponent savedComponent = createFeatureComponentInDb("ForbiddenFeature");
        mockMvc.perform(delete("/api/v1/features/{id}", savedComponent.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    // ADMIN has both delete and read permissions
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldDeleteFeatureAndReturn204() throws Exception {
        FeatureComponent savedComponent = createFeatureComponentInDb("DeletableFeature");
        Long idToDelete = savedComponent.getId();

        mockMvc.perform(delete("/api/v1/features/{id}", idToDelete))
                .andExpect(status().isNoContent()); // Expect 204 No Content

        // Verify it was actually deleted from the repository (requires 'read' permission)
        mockMvc.perform(get("/api/v1/features/{id}", idToDelete))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn409WhenDeletingLinkedFeature() throws Exception {
        // Create a feature component
        FeatureComponent linkedComponent = createFeatureComponentInDb("LinkedFeature");

        // Create the ProductFeatureLink using the static, committed Product
        ProductFeatureLink link = new ProductFeatureLink();
        link.setFeatureComponent(linkedComponent);
        link.setProduct(sharedProduct);
        link.setFeatureValue("Default Value Set");

        txHelper.doInTransaction(() -> {
            linkRepository.save(link);
        });

        // Attempt to delete the linked component
        mockMvc.perform(delete("/api/v1/features/{id}", linkedComponent.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("Cannot delete Feature Component ID")));
    }
}