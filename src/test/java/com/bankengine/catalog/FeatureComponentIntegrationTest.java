package com.bankengine.catalog;

import com.bankengine.auth.config.test.WithMockRole;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class FeatureComponentIntegrationTest {

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
    private TestTransactionHelper txHelper;

    // Shared data entities
    private Product sharedProduct;

    // --- Role Constants ---
    public static final String ROLE_PREFIX = "FCIT_";
    private static final String ADMIN_ROLE = ROLE_PREFIX + "CATALOG_ADMIN";
    private static final String READER_ROLE = ROLE_PREFIX + "CATALOG_READER";

    /**
     * Set up all required roles and permissions once before all tests run.
     */
    @BeforeAll
    static void setupRoles(@Autowired TestTransactionHelper txHelper) {
        // ADMIN has all permissions needed for CRUD
        Set<String> adminAuths = Set.of(
                "catalog:feature:create",
                "catalog:feature:read",
                "catalog:feature:update",
                "catalog:feature:delete"
        );
        // READER has only read permission
        Set<String> readerAuths = Set.of("catalog:feature:read");

        // Commit the roles in separate transactions
        txHelper.createRoleInDb(ADMIN_ROLE, adminAuths);
        txHelper.createRoleInDb(READER_ROLE, readerAuths);
    }

    @BeforeEach
    void setup() {
        setupUniqueDependencies();
    }

    void setupUniqueDependencies() {
        // Use the transaction helper to ensure the unique product types and products are created
        // and committed only if they don't exist.
        txHelper.doInTransaction(() -> {
            // 1. Check/Create a minimal ProductType dependency first
            ProductType sharedProductType = productTypeRepository.findByName("Test Type for Link")
                    .orElseGet(() -> {
                        ProductType newType = new ProductType();
                        newType.setName("Test Type for Link");
                        return productTypeRepository.save(newType);
                    });

            // Set the instance variable for use in tests
            this.sharedProduct = productRepository.findByName("Link Test Product")
                    .orElseGet(() -> {
                        // 2. Check/Create a minimal Product entity to link to
                        Product product = new Product();
                        product.setName("Link Test Product");
                        product.setStatus("ACTIVE");
                        product.setProductType(sharedProductType);
                        return productRepository.save(product);
                    });
        });
    }

    // Helper method to create a valid DTO for POST/PUT requests
    private CreateFeatureComponentRequestDto getCreateDto(String name) {
        CreateFeatureComponentRequestDto dto = new CreateFeatureComponentRequestDto();
        dto.setName(name);
        dto.setDataType("STRING");
        return dto;
    }

    // Helper method to create an entity directly in the DB
    // NOTE: This helper is called by tests that rely on committed data (e.g., GET tests)
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
    @WithMockRole(roles = {}) // <-- No roles assigned, user is logged in but unauthorized
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

        // Create the ProductFeatureLink using the Product
        ProductFeatureLink link = new ProductFeatureLink();
        link.setFeatureComponent(linkedComponent);
        link.setProduct(sharedProduct);
        link.setFeatureValue("Default Value Set");

        linkRepository.save(link);

        // Attempt to delete the linked component
        mockMvc.perform(delete("/api/v1/features/{id}", linkedComponent.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("Cannot delete Feature Component ID")));
    }
}