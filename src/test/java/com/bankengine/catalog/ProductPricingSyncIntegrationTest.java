package com.bankengine.catalog;

import com.bankengine.auth.security.TenantContextHolder;
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
import com.bankengine.test.config.WithMockRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
//    @Autowired private EntityManager entityManager;

    public static final String ROLE_PREFIX = "PPST_";
    public static final String ADMIN_ROLE = ROLE_PREFIX + "CATALOG_ADMIN";
    public static final String READER_ROLE = ROLE_PREFIX + "CATALOG_READER";
    public static final String UNAUTHORIZED_ROLE = ROLE_PREFIX + "UNAUTHORIZED_ROLE";

    private static Long productId;
    private static Long compRateId;
    private static Long compFeeId;
    private static Long compDiscountId;

    @BeforeAll
    static void setupCommittedData(@Autowired TestTransactionHelper txHelperStatic,
                                   @Autowired ProductRepository productRepoStatic,
                                   @Autowired ProductTypeRepository productTypeRepoStatic,
                                   @Autowired PricingComponentRepository pricingComponentRepoStatic) {

        seedBaseRoles(txHelperStatic, Map.of(
            ADMIN_ROLE, Set.of("catalog:product:update", "catalog:product:read"),
            READER_ROLE, Set.of("catalog:product:read"),
            UNAUTHORIZED_ROLE, Set.of("some:other:permission")
        ));

        try {
            TenantContextHolder.setBankId(TEST_BANK_ID);

            txHelperStatic.doInTransaction(() -> {
                ProductType type = productTypeRepoStatic.findByName("Savings Account")
                        .orElseGet(() -> {
                            ProductType nt = new ProductType();
                            nt.setName("Savings Account");
                            return productTypeRepoStatic.save(nt);
                        });

                Product p = productRepoStatic.findByName("Pricing Sync Product")
                        .orElseGet(() -> {
                            Product np = new Product();
                            np.setName("Pricing Sync Product");
                            np.setProductType(type);
                            np.setCategory("RETAIL");
                            return productRepoStatic.save(np);
                        });
                productId = p.getId();

                compRateId = pricingComponentRepoStatic.save(new PricingComponent("Standard Interest Rate", PricingComponent.ComponentType.INTEREST_RATE)).getId();
                compFeeId = pricingComponentRepoStatic.save(new PricingComponent("Monthly Maintenance Fee", PricingComponent.ComponentType.FEE)).getId();
                compDiscountId = pricingComponentRepoStatic.save(new PricingComponent("Loyalty Discount", PricingComponent.ComponentType.DISCOUNT)).getId();
            });
        } finally {
            TenantContextHolder.clear();
        }
    }

    @BeforeEach
    @AfterEach
    void cleanUpLinks() {
        txHelper.doInTransaction(() -> {
            pricingLinkRepository.deleteAllInBatch(pricingLinkRepository.findByProductId(productId));
        });
        entityManager.clear();
    }

    // --- Helpers ---

    private ProductPricing createPricingDto(Long componentId) {
        ProductPricing dto = new ProductPricing();
        dto.setPricingComponentId(componentId);
        return dto;
    }

    private ProductPricingLink createInitialLink(Long pId, Long pcId) {
        ProductPricingLink link = new ProductPricingLink();
        link.setProduct(entityManager.getReference(Product.class, pId));
        link.setPricingComponent(entityManager.getReference(PricingComponent.class, pcId));
        link.setBankId(TEST_BANK_ID);
        return link;
    }

    // --- Tests ---

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void shouldReturn403WhenSyncingPricingWithoutPermission() throws Exception {
        List<ProductPricing> requests = List.of(createPricingDto(compRateId));

        mockMvc.perform(put("/api/v1/products/{id}/pricing", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldCreateNewPricingLinksWhenNoneExist() throws Exception {
        List<ProductPricing> requests = List.of(
                createPricingDto(compRateId),
                createPricingDto(compFeeId)
        );

        mockMvc.perform(put("/api/v1/products/{id}/pricing", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk());

        txHelper.doInTransaction(() -> {
            assertThat(pricingLinkRepository.findByProductId(productId)).hasSize(2);
        });
    }

    @Test
    void shouldPerformFullSync_CreateAndDelete() throws Exception {
        // ARRANGE: Seed initial state
        txHelper.doInTransaction(() -> {
            pricingLinkRepository.save(createInitialLink(productId, compRateId));
            pricingLinkRepository.save(createInitialLink(productId, compDiscountId));
        });

        // ACT: Request sync (Keep Rate, Add Fee, implicitly Delete Discount)
        List<ProductPricing> requests = List.of(
                createPricingDto(compRateId),
                createPricingDto(compFeeId)
        );

        mockMvc.perform(put("/api/v1/products/{id}/pricing", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk());

        // VERIFY
        txHelper.doInTransaction(() -> {
            List<ProductPricingLink> finalLinks = pricingLinkRepository.findByProductId(productId);
            assertThat(finalLinks).hasSize(2);
            assertThat(pricingLinkRepository.existsByPricingComponentIdAndProductId(compDiscountId, productId)).isFalse();
        });
    }

    @Test
    void shouldReturn404OnSyncWithNonExistentProductOrComponent() throws Exception {
        // Test 1: Non-existent Product ID
        mockMvc.perform(put("/api/v1/products/99999/pricing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(createPricingDto(compRateId)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Product not found")));

        // Test 2: Non-existent Pricing Component ID
        List<ProductPricing> badLinks = List.of(createPricingDto(99999L));

        mockMvc.perform(put("/api/v1/products/{id}/pricing", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLinks)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Pricing Component not found")));
    }
}