package com.bankengine.catalog;

import com.bankengine.auth.config.test.WithMockRole;
import com.bankengine.auth.security.BankContextHolder;
import com.bankengine.catalog.dto.ProductFeature;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockRole(roles = {ProductFeatureSyncIntegrationTest.ADMIN_ROLE})
public class ProductFeatureSyncIntegrationTest extends AbstractIntegrationTest {

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

    @Autowired
    private EntityManager entityManager;

    // --- Role Constants ---
    public static final String ROLE_PREFIX = "PFST_";
    public static final String ADMIN_ROLE = ROLE_PREFIX + "CATALOG_ADMIN";
    public static final String READER_ROLE = ROLE_PREFIX + "CATALOG_READER";

    // Entities used for setup
    private static Product product;
    private static FeatureComponent componentA; // Integer type, value "10"
    private static FeatureComponent componentB; // Boolean type, value "true"
    private static FeatureComponent componentC; // Decimal type, value "5.50"


    /**
     * Set up all required roles, permissions, and shared committed entities once before all tests run.
     */
    @BeforeAll
    static void setupCommittedData(@Autowired TestTransactionHelper txHelperStatic,
                                   @Autowired ProductRepository productRepoStatic,
                                   @Autowired ProductTypeRepository productTypeRepoStatic,
                                   @Autowired FeatureComponentRepository featureComponentRepoStatic) {

        BankContextHolder.setBankId(TEST_BANK_ID);
        // 1. Setup Roles (Committed)
        Set<String> adminAuths = Set.of(
                "catalog:product:update",
                "catalog:feature:create",
                "catalog:feature:update",
                "catalog:feature:read"
        );
        Set<String> readerAuths = Set.of("catalog:product:read");

        txHelperStatic.createRoleInDb(ADMIN_ROLE, adminAuths);
        txHelperStatic.createRoleInDb(READER_ROLE, readerAuths);

        // 2. Setup Product and Components (Committed Transaction)
        txHelperStatic.doInTransaction(() -> {

            // Helper function to find or create a feature component
            BiFunction<String, FeatureComponent.DataType, FeatureComponent> findOrCreateFeatureComponent = (name, dataType) ->
                 featureComponentRepoStatic.findByName(name)
                    .orElseGet(() -> {
                        FeatureComponent component = new FeatureComponent();
                        component.setName(name);
                        component.setDataType(dataType);
                        return featureComponentRepoStatic.save(component);
                    });

            // Setup ProductType (find-or-create)
            ProductType type = productTypeRepoStatic.findByName("Checking Account")
                    .orElseGet(() -> {
                        ProductType newType = new ProductType();
                        newType.setName("Checking Account");
                        return productTypeRepoStatic.save(newType);
                    });

            // Setup Product (find-or-create)
            product = productRepoStatic.findByName("Sync Test Product")
                    .orElseGet(() -> {
                        Product p = new Product();
                        p.setName("Sync Test Product");
                        p.setProductType(type);
                        return productRepoStatic.save(p);
                    });

            // Setup Feature Components (find-or-create)
            componentA = findOrCreateFeatureComponent.apply("Monthly Limit", FeatureComponent.DataType.INTEGER);
            componentB = findOrCreateFeatureComponent.apply("Free ATM Access", FeatureComponent.DataType.BOOLEAN);
            componentC = findOrCreateFeatureComponent.apply("Cashback Rate", FeatureComponent.DataType.DECIMAL);
        });

        txHelperStatic.flushAndClear();
    }


    @BeforeEach
    void setup() {
        // CLEANUP: Ensure a clean slate of LINKS before every test.
        // This MUST run in its own committed transaction.
        txHelper.doInTransaction(() -> {
            // Find links related to the static product and delete them.
            linkRepository.deleteAllInBatch(linkRepository.findByProductId(product.getId()));
        });

        // Clear EntityManager cache just in case the link repository has stale data
        entityManager.clear();
    }

    // =================================================================
    // HELPER METHODS
    // =================================================================

    // Helper method to create DTOs
    private ProductFeature createFeatureDto(FeatureComponent component, String value) {
        ProductFeature dto = new ProductFeature();
        dto.setFeatureComponentId(component.getId());
        dto.setFeatureValue(value);
        return dto;
    }

    // Helper to create an initial link entity
    private ProductFeatureLink createInitialLink(Product p, FeatureComponent fc, String value) {
        ProductFeatureLink link = new ProductFeatureLink();
        link.setProduct(p);
        link.setFeatureComponent(fc);
        link.setFeatureValue(value);
        return link;
    }


    // =================================================================
    // 0. SECURITY TEST
    // =================================================================

    @Test
    @WithMockRole(roles = {READER_ROLE}) // <-- User has read permission, lacks update
    void shouldReturn403WhenSyncingFeaturesWithoutPermission() throws Exception {
        List<ProductFeature> features = List.of(createFeatureDto(componentA, "20"));

        mockMvc.perform(put("/api/v1/products/{id}/features", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(features)))
                .andExpect(status().isForbidden());
    }

    // =================================================================
    // 1. INITIAL CREATE (No existing links)
    // All subsequent tests use the default @WithMockRole(roles = {ADMIN_ROLE})
    // =================================================================

    @Test
    void shouldCreateNewFeaturesWhenNoLinksExist() throws Exception {
        List<ProductFeature> features = List.of(
                createFeatureDto(componentA, "20"), // New link A
                createFeatureDto(componentB, "false") // New link B
        );

        // ACT: Synchronize
        mockMvc.perform(put("/api/v1/products/{id}/features", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(features)))
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
        List<ProductFeature> features = List.of(
                createFeatureDto(componentA, "50"), // A is updated (10 -> 50)
                createFeatureDto(componentB, "true") // B is created
        );
        // Note: Component C (3.0) is missing from the list, so it should be deleted.

        // ACT: Synchronize
        mockMvc.perform(put("/api/v1/products/{id}/features", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(features)))
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
            assertThat(finalLinks.stream().anyMatch(l -> l.getFeatureComponent().getId().equals(componentC.getId()))).isFalse();
        });
    }

    // =================================================================
    // 3. ERROR HANDLING (Validation)
    // =================================================================

    @Test
    void shouldReturn400OnSyncWithInvalidFeatureValueType() throws Exception {
        // ARRANGE: Attempt to set a STRING value on a BOOLEAN feature (B)
        List<ProductFeature> features = List.of(
                createFeatureDto(componentB, "Not a boolean value")
        );

        // ACT & ASSERT: Expect 400 Bad Request due to validation failure
        mockMvc.perform(put("/api/v1/products/{id}/features", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(features)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("must be 'true' or 'false' for BOOLEAN")));
    }

    @Test
    void shouldReturn404OnSyncWithNonExistentProduct() throws Exception {
        // Use an existing FeatureComponent ID for a valid DTO structure
        List<ProductFeature> features = List.of(createFeatureDto(componentA, "1"));

        // ACT & ASSERT: Use a non-existent Product ID (99999)
        mockMvc.perform(put("/api/v1/products/99999/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(features)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Product not found")));
    }
}