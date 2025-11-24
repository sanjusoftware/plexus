package com.bankengine.catalog;

import com.bankengine.auth.config.test.WithMockRole;
import com.bankengine.catalog.dto.ProductFeatureDto;
import com.bankengine.catalog.dto.ProductFeatureSyncDto;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockRole(roles = {ProductFeatureSyncIntegrationTest.ADMIN_ROLE})
public class ProductFeatureSyncIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductTypeRepository productTypeRepository;

    @Autowired
    private FeatureComponentRepository featureComponentRepository;

    @Autowired
    private ProductFeatureLinkRepository linkRepository;

    @Autowired
    private TestTransactionHelper txHelper;

    // --- Role Constants ---
    public static final String ADMIN_ROLE = "CATALOG_ADMIN";
    public static final String READER_ROLE = "CATALOG_READER";

    // Entities used for setup
    private Product product;
    private FeatureComponent componentA; // Integer type, value "10"
    private FeatureComponent componentB; // Boolean type, value "true"
    private FeatureComponent componentC; // Decimal type, value "5.50"

    /**
     * Set up all required roles and permissions once before all tests run.
     */
    @BeforeAll
    static void setupRoles(@Autowired TestTransactionHelper txHelper) {
        // ADMIN needs the update permission for this entire class
        Set<String> adminAuths = Set.of(
                "catalog:product:update"
        );
        // READER can be used for denial (lacks the update permission)
        Set<String> readerAuths = Set.of("catalog:product:read");

        txHelper.createRoleInDb(ADMIN_ROLE, adminAuths);
        txHelper.createRoleInDb(READER_ROLE, readerAuths);
    }

    // Helper method to create DTOs
    private ProductFeatureDto createFeatureDto(FeatureComponent component, String value) {
        ProductFeatureDto dto = new ProductFeatureDto();
        dto.setProductId(product.getId());
        dto.setFeatureComponentId(component.getId());
        dto.setFeatureValue(value);
        return dto;
    }

    @BeforeEach
    void setup() {
        // PART 1: Find-or-Create unique dependencies (Product, Components)
        // This only runs inserts if the data isn't already committed.
        txHelper.doInTransaction(() -> {
            // 1. Setup ProductType (find-or-create)
            ProductType type = productTypeRepository.findByName("Checking Account")
                    .orElseGet(() -> {
                        ProductType newType = new ProductType();
                        newType.setName("Checking Account");
                        return productTypeRepository.save(newType);
                    });

            // 2. Setup Product (find-or-create)
            product = productRepository.findByName("Sync Test Product")
                    .orElseGet(() -> {
                        Product p = new Product();
                        p.setName("Sync Test Product");
                        p.setProductType(type);
                        return productRepository.save(p);
                    });

            // 3. Setup Feature Components (find-or-create)
            componentA = findOrCreateFeatureComponent("Monthly Limit", FeatureComponent.DataType.INTEGER);
            componentB = findOrCreateFeatureComponent("Free ATM Access", FeatureComponent.DataType.BOOLEAN);
            componentC = findOrCreateFeatureComponent("Cashback Rate", FeatureComponent.DataType.DECIMAL);
        });

        // PART 2: CLEANUP - Ensure a clean slate of LINKS before every test
        // This MUST run after Part 1 to ensure 'product' is initialized.
        // It must also run in its own committed transaction.
        txHelper.doInTransaction(() -> {
            linkRepository.deleteAll(linkRepository.findByProductId(product.getId()));
            linkRepository.flush();
        });
    }

    // New helper method for find-or-create pattern
    private FeatureComponent findOrCreateFeatureComponent(String name, FeatureComponent.DataType dataType) {
        // Requires findByName in FeatureComponentRepository
        return featureComponentRepository.findByName(name)
                .orElseGet(() -> {
                    FeatureComponent component = new FeatureComponent();
                    component.setName(name);
                    component.setDataType(dataType);
                    return featureComponentRepository.save(component);
                });
    }

    // =================================================================
    // 0. SECURITY TEST
    // =================================================================

    @Test
    @WithMockRole(roles = {READER_ROLE}) // <-- User has read permission, lacks update
    void shouldReturn403WhenSyncingFeaturesWithoutPermission() throws Exception {
        ProductFeatureSyncDto syncDto = new ProductFeatureSyncDto();
        syncDto.setFeatures(List.of(createFeatureDto(componentA, "20")));

        mockMvc.perform(put("/api/v1/products/{id}/features", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncDto)))
                .andExpect(status().isForbidden());
    }

    // =================================================================
    // 1. INITIAL CREATE (No existing links)
    // All subsequent tests use the default @WithMockRole(roles = {ADMIN_ROLE})
    // =================================================================

    @Test
    void shouldCreateNewFeaturesWhenNoLinksExist() throws Exception {
        // ARRANGE: Cleanup in @BeforeEach ensures no links exist here.
        ProductFeatureSyncDto syncDto = new ProductFeatureSyncDto();
        syncDto.setFeatures(List.of(
                createFeatureDto(componentA, "20"), // New link A
                createFeatureDto(componentB, "false") // New link B
        ));

        // ACT: Synchronize
        mockMvc.perform(put("/api/v1/products/{id}/features", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()", is(2)));

        // VERIFY: Check DB count
        txHelper.doInTransaction(() -> {
            assertThat(linkRepository.findByProductId(product.getId())).hasSize(2);
        });
    }

    // =================================================================
    // 2. CREATE, UPDATE, DELETE (Full Sync)
    // =================================================================

    @Test
    void shouldPerformFullSync_CreateUpdateAndDelete() throws Exception {
        // ARRANGE: Setup initial state (Only A and C linked). Needs to be committed.
        txHelper.doInTransaction(() -> {
            linkRepository.save(createInitialLink(product, componentA, "10")); // Initial A
            linkRepository.save(createInitialLink(product, componentC, "3.0")); // Initial C
        });

        // ARRANGE: Target state (B is created, A is updated, C is deleted)
        ProductFeatureSyncDto syncDto = new ProductFeatureSyncDto();
        syncDto.setFeatures(List.of(
                createFeatureDto(componentA, "50"), // A is updated (10 -> 50)
                createFeatureDto(componentB, "true") // B is created
        ));
        // Note: Component C (3.0) is missing from the list, so it should be deleted.

        // ACT: Synchronize
        mockMvc.perform(put("/api/v1/products/{id}/features", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()", is(2)));

        // VERIFY: Check DB final state
        txHelper.doInTransaction(() -> {
            List<ProductFeatureLink> finalLinks = linkRepository.findByProductId(product.getId());
            assertThat(finalLinks.stream().map(l -> l.getFeatureComponent().getId()).collect(Collectors.toSet()))
                    .containsExactlyInAnyOrder(componentA.getId(), componentB.getId());

            // Check update (A's value should be 50)
            ProductFeatureLink linkA = finalLinks.stream()
                    .filter(l -> l.getFeatureComponent().getId().equals(componentA.getId()))
                    .findFirst().orElseThrow();
            assertThat(linkA.getFeatureValue()).isEqualTo("50");

            // Check deletion (C link should be gone)
            assertThat(linkRepository.findByFeatureComponentId(componentC.getId())).isEmpty();
        });
    }

    // =================================================================
    // 3. ERROR HANDLING (Validation)
    // =================================================================

    @Test
    void shouldReturn400OnSyncWithInvalidFeatureValueType() throws Exception {
        // ARRANGE: Attempt to set a STRING value on a BOOLEAN feature (B)
        ProductFeatureSyncDto syncDto = new ProductFeatureSyncDto();
        syncDto.setFeatures(List.of(
                createFeatureDto(componentB, "Not a boolean value")
        ));

        // ACT & ASSERT: Expect 400 Bad Request due to validation failure
        mockMvc.perform(put("/api/v1/products/{id}/features", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("must be 'true' or 'false' for BOOLEAN")));
    }

    @Test
    void shouldReturn404OnSyncWithNonExistentProduct() throws Exception {
        ProductFeatureSyncDto syncDto = new ProductFeatureSyncDto();
        // Use an existing FeatureComponent ID for a valid DTO structure
        syncDto.setFeatures(List.of(createFeatureDto(componentA, "1")));

        // ACT & ASSERT: Use a non-existent Product ID (99999)
        mockMvc.perform(put("/api/v1/products/99999/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Product not found")));
    }

    // =================================================================
    // Helper to create an initial link entity
    // =================================================================
    private ProductFeatureLink createInitialLink(Product p, FeatureComponent fc, String value) {
        ProductFeatureLink link = new ProductFeatureLink();
        link.setProduct(p);
        link.setFeatureComponent(fc);
        link.setFeatureValue(value);
        return link;
    }
}