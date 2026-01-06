package com.bankengine.catalog;

import com.bankengine.auth.config.test.WithMockRole;
import com.bankengine.catalog.dto.ProductPricing;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.pricing.TestTransactionHelper;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.ProductPricingLink;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.repository.ProductPricingLinkRepository;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockRole(roles = {ProductPricingSyncIntegrationTest.ADMIN_ROLE})
public class ProductPricingSyncIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductTypeRepository productTypeRepository;
    @Autowired private PricingComponentRepository pricingComponentRepository;
    @Autowired private ProductPricingLinkRepository pricingLinkRepository;
    @Autowired private TestTransactionHelper txHelper;
    @Autowired private EntityManager entityManager;

    // --- Role Constants ---
    public static final String ROLE_PREFIX = "PPST_";
    public static final String ADMIN_ROLE = ROLE_PREFIX + "CATALOG_ADMIN";
    public static final String READER_ROLE = ROLE_PREFIX + "CATALOG_READER";
    public static final String UNAUTHORIZED_ROLE = ROLE_PREFIX + "UNAUTHORIZED_ROLE";

    // Entities used for setup - CHANGED to static and initialized in @BeforeAll
    private static Product product;
    private static PricingComponent compRate; // CORE_RATE context
    private static PricingComponent compFee; // ANNUAL_FEE context
    private static PricingComponent compDiscount; // DISCOUNT context

    // =================================================================
    // SETUP AND TEARDOWN (Committed Data Lifecycle)
    // =================================================================

    /**
     * Set up all required roles and permanent committed entities once before all tests run.
     */
    @BeforeAll
    static void setupCommittedData(@Autowired TestTransactionHelper txHelperStatic,
                                   @Autowired ProductRepository productRepoStatic,
                                   @Autowired ProductTypeRepository productTypeRepoStatic,
                                   @Autowired PricingComponentRepository pricingComponentRepoStatic) {

        // 1. Setup Roles (Committed)
        Set<String> adminAuths = Set.of("catalog:product:update"); // Only need update permission for this class
        Set<String> readerAuths = Set.of("catalog:product:read");
        Set<String> unauthorizedAuths = Set.of("some:other:permission");

        txHelperStatic.createRoleInDb(ADMIN_ROLE, adminAuths);
        txHelperStatic.createRoleInDb(READER_ROLE, readerAuths);
        txHelperStatic.createRoleInDb(UNAUTHORIZED_ROLE, unauthorizedAuths);

        // 2. Setup Product and Components (Committed Transaction - Find or Create)
        txHelperStatic.doInTransaction(() -> {
            // a. Setup ProductType
            ProductType type = productTypeRepoStatic.findByName("Savings Account")
                    .orElseGet(() -> {
                        ProductType newType = new ProductType();
                        newType.setName("Savings Account");
                        newType.setBankId(TEST_BANK_ID);
                        return productTypeRepoStatic.save(newType);
                    });

            // b. Setup Product
            product = productRepoStatic.findByName("Pricing Sync Product")
                    .orElseGet(() -> {
                        Product p = new Product();
                        p.setName("Pricing Sync Product");
                        p.setProductType(type);
                        p.setBankId(TEST_BANK_ID);
                        return productRepoStatic.save(p);
                    });

            // c. Setup Pricing Components
            compRate = pricingComponentRepoStatic.findByName("Standard Interest Rate")
                    .orElseGet(() -> {
                        PricingComponent c = new PricingComponent();
                        c.setName("Standard Interest Rate");
                        c.setType(PricingComponent.ComponentType.RATE);
                        c.setBankId(TEST_BANK_ID);
                        return pricingComponentRepoStatic.save(c);
                    });

            compFee = pricingComponentRepoStatic.findByName("Monthly Maintenance Fee")
                    .orElseGet(() -> {
                        PricingComponent c = new PricingComponent();
                        c.setName("Monthly Maintenance Fee");
                        c.setType(PricingComponent.ComponentType.FEE);
                        c.setBankId(TEST_BANK_ID);
                        return pricingComponentRepoStatic.save(c);
                    });

            compDiscount = pricingComponentRepoStatic.findByName("Loyalty Discount")
                    .orElseGet(() -> {
                        PricingComponent c = new PricingComponent();
                        c.setName("Loyalty Discount");
                        c.setType(PricingComponent.ComponentType.DISCOUNT);
                        c.setBankId(TEST_BANK_ID);
                        return pricingComponentRepoStatic.save(c);
                    });
        });

        txHelperStatic.flushAndClear();
    }

    /**
     * Clean up ALL committed ProductPricingLinks created during a test run.
     */
    @BeforeEach
    @AfterEach
    void cleanUpLinks() {
        // Runs before AND after each test to ensure isolation and cleanup of committed links
        txHelper.doInTransaction(() -> {
            if (product != null) {
                // Delete only the links associated with the shared product
                pricingLinkRepository.deleteAllInBatch(pricingLinkRepository.findByProductId(product.getId()));
            }
        });
        entityManager.clear();
    }


    // =================================================================
    // HELPER METHODS
    // =================================================================

    // Helper method to create a DTO for synchronization
    private ProductPricing createPricingDto(PricingComponent component, String context) {
        ProductPricing dto = new ProductPricing();
        dto.setPricingComponentId(component.getId());
        dto.setContext(context);
        return dto;
    }

    // Helper method to create an initial link entity (used for ARRANGE step)
    private ProductPricingLink createInitialLink(Product p, PricingComponent pc, String context) {
        ProductPricingLink link = new ProductPricingLink();
        link.setProduct(p);
        link.setPricingComponent(pc);
        link.setContext(context);
        return link;
    }

    // =================================================================
    // 0. SECURITY TEST
    // =================================================================

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void shouldReturn403WhenSyncingPricingWithoutPermission() throws Exception {
        List<ProductPricing> requests = List.of(createPricingDto(compRate, "RATE"));

        mockMvc.perform(put("/api/v1/products/{id}/pricing", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isForbidden());
    }

    // =================================================================
    // 1. INITIAL CREATE (No existing links)
    // =================================================================

    @Test
    void shouldCreateNewPricingLinksWhenNoneExist() throws Exception {
        List<ProductPricing> requests = List.of(
                createPricingDto(compRate, "CORE_RATE"),
                createPricingDto(compFee, "MONTHLY_FEE")
        );

        // ACT: Synchronize
        mockMvc.perform(put("/api/v1/products/{id}/pricing", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk());

        // VERIFY: Check DB count (requires dedicated transaction since MockMvc commits)
        txHelper.doInTransaction(() -> {
            assertThat(pricingLinkRepository.findByProductId(product.getId())).hasSize(2);
        });
    }

    // =================================================================
    // 2. CREATE and DELETE (Full Sync)
    // =================================================================

    @Test
    void shouldPerformFullSync_CreateAndDelete() throws Exception {
        // ARRANGE: Setup initial state (Only compRate and compDiscount linked). Must be committed.
        txHelper.doInTransaction(() -> {
            pricingLinkRepository.save(createInitialLink(product, compRate, "CORE_RATE")); // Initial Rate
            pricingLinkRepository.save(createInitialLink(product, compDiscount, "LOYALTY_DISCOUNT")); // Initial Discount
        });

        // ARRANGE: Target state (compFee is created, compDiscount is deleted)
        List<ProductPricing> requests = List.of(
                createPricingDto(compRate, "CORE_RATE"), // Remains unchanged
                createPricingDto(compFee, "ANNUAL_FEE") // New fee link created
        );
        // compDiscount link is missing, so the API should delete it.

        // ACT: Synchronize
        mockMvc.perform(put("/api/v1/products/{id}/pricing", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk());

        // VERIFY 1: Check DB final state
        txHelper.doInTransaction(() -> {
            List<ProductPricingLink> finalLinks = pricingLinkRepository.findByProductId(product.getId());
            assertThat(finalLinks).hasSize(2);

            // VERIFY 2: Check composition (should contain CORE_RATE and ANNUAL_FEE contexts)
            List<String> finalContexts = finalLinks.stream()
                    .map(ProductPricingLink::getContext)
                    .collect(Collectors.toList());
            assertThat(finalContexts).containsExactlyInAnyOrder("CORE_RATE", "ANNUAL_FEE");

            // VERIFY 3: Check deletion (Discount link should be gone)
            assertThat(pricingLinkRepository.existsByPricingComponentId(compDiscount.getId())).isFalse();
        });
    }

    // =================================================================
    // 3. ERROR HANDLING (Not Found)
    // =================================================================

    @Test
    void shouldReturn404OnSyncWithNonExistentProductOrComponent() throws Exception {
        // Use the committed Product ID
        Long existingProductId = product.getId();

        // Test 1: Non-existent Product ID
        mockMvc.perform(put("/api/v1/products/99999/pricing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(createPricingDto(compRate, "RATE")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Product not found")));

        // Test 2: Non-existent Pricing Component ID
        List<ProductPricing> badLinks = List.of(
                new ProductPricing() {{
                    setPricingComponentId(99999L); // ID that doesn't exist
                    setContext("BAD_LINK");
                }}
        );

        mockMvc.perform(put("/api/v1/products/{id}/pricing", existingProductId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLinks)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Pricing Component not found")));
    }
}