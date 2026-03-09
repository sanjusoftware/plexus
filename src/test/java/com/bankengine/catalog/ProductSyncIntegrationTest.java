package com.bankengine.catalog;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.dto.ProductFeatureDto;
import com.bankengine.catalog.dto.ProductPricingDto;
import com.bankengine.catalog.dto.ProductRequest;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.FeatureComponentRepository;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.common.model.VersionableEntity;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ProductSyncIntegrationTest extends AbstractIntegrationTest {

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
    private PricingComponentRepository pricingComponentRepository;
    @Autowired
    private ProductFeatureLinkRepository featureLinkRepository;
    @Autowired
    private ProductPricingLinkRepository pricingLinkRepository;
    @Autowired
    private TestTransactionHelper txHelper;

    private static Long EXISTING_PRODUCT_TYPE_ID;
    private static Long EXISTING_PRICING_COMP_ID;
    private final String PRODUCT_API_BASE = "/api/v1/products";

    public static final String ROLE_PREFIX = "PSYT_";
    private static final String ADMIN_ROLE = ROLE_PREFIX + "CATALOG_ADMIN";

    @BeforeAll
    static void setupRoles(@Autowired TestTransactionHelper txHelperStatic,
                           @Autowired ProductTypeRepository productTypeRepoStatic,
                           @Autowired PricingComponentRepository pricingRepoStatic) {
        seedBaseRoles(txHelperStatic, Map.of(
                ADMIN_ROLE, Set.of("catalog:product:update", "catalog:product:read")
        ));

        try {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            txHelperStatic.doInTransaction(() -> {
                ProductType type = ProductType.builder()
                        .name("Sync Test Type")
                        .code("STT")
                        .bankId(TEST_BANK_ID)
                        .status(VersionableEntity.EntityStatus.ACTIVE)
                        .build();
                EXISTING_PRODUCT_TYPE_ID = productTypeRepoStatic.save(type).getId();

                PricingComponent comp = PricingComponent.builder()
                        .name("Base Fee").code("BASE_01").type(PricingComponent.ComponentType.FEE).build();
                EXISTING_PRICING_COMP_ID = pricingRepoStatic.save(comp).getId();
            });
        } finally {
            TenantContextHolder.clear();
        }
    }

    @AfterEach
    void tearDown() {
        txHelper.doInTransaction(() -> {
            featureLinkRepository.deleteAllInBatch();
            pricingLinkRepository.deleteAllInBatch();
            productRepository.deleteAllInBatch();
        });
    }

    // --- Helpers ---

    private Long createDraftProductInDb(String name) {
        return txHelper.doInTransaction(() -> {
            Product product = Product.builder()
                    .name(name)
                    .code("CODE_" + UUID.randomUUID())
                    .productType(productTypeRepository.getReferenceById(EXISTING_PRODUCT_TYPE_ID))
                    .category("RETAIL")
                    .status(VersionableEntity.EntityStatus.DRAFT)
                    .build();
            return productRepository.save(product).getId();
        });
    }

    // --- Tests ---

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    @DisplayName("Feature Sync - Should handle Add, Update, and Orphan Removal")
    void shouldPerformFullFeatureSync() throws Exception {
        Long productId = createDraftProductInDb("Feature Sync Product");

        Map<String, FeatureComponent> comps = txHelper.doInTransaction(() -> {
            FeatureComponent limit = featureComponentRepository.save(FeatureComponent.builder()
                    .name("Limit").code("L1").dataType(FeatureComponent.DataType.INTEGER).build());
            FeatureComponent access = featureComponentRepository.save(FeatureComponent.builder()
                    .name("Access").code("A1").dataType(FeatureComponent.DataType.BOOLEAN).build());

            Product product = productRepository.getReferenceById(productId);
            featureLinkRepository.save(ProductFeatureLink.builder()
                    .product(product).featureComponent(limit).featureValue("100").build());

            return Map.of("LIMIT", limit, "ACCESS", access);
        });

        ProductRequest syncRequest = ProductRequest.builder()
                .features(List.of(
                        ProductFeatureDto.builder().featureComponentId(comps.get("LIMIT").getId()).featureValue("200").build(),
                        ProductFeatureDto.builder().featureComponentId(comps.get("ACCESS").getId()).featureValue("true").build()
                )).build();

        mockMvc.perform(patch(PRODUCT_API_BASE + "/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncRequest)))
                .andExpect(status().isOk());

        txHelper.doInTransaction(() -> {
            List<ProductFeatureLink> links = featureLinkRepository.findByProductId(productId);
            assertThat(links).hasSize(2);
            assertThat(links).filteredOn(l -> l.getFeatureComponent().getName().equals("Limit"))
                    .extracting(ProductFeatureLink::getFeatureValue).containsExactly("200");
        });
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    @DisplayName("Pricing Sync - Should handle Add, Update, and Orphan Removal")
    void shouldPerformFullPricingSync() throws Exception {
        Long productId = createDraftProductInDb("Pricing Sync Product");

        Map<String, PricingComponent> comps = txHelper.doInTransaction(() -> {
            PricingComponent fee = pricingComponentRepository.save(PricingComponent.builder()
                    .name("Monthly Fee").code("FEE1").type(PricingComponent.ComponentType.FEE).build());
            PricingComponent tax = pricingComponentRepository.save(PricingComponent.builder()
                    .name("Tax").code("TAX1").type(PricingComponent.ComponentType.TAX).build());

            Product product = productRepository.getReferenceById(productId);
            pricingLinkRepository.save(ProductPricingLink.builder()
                    .product(product).pricingComponent(fee).fixedValue(BigDecimal.valueOf(10.0)).build());

            return Map.of("FEE", fee, "TAX", tax);
        });

        ProductRequest syncRequest = ProductRequest.builder()
                .pricing(List.of(
                        ProductPricingDto.builder().pricingComponentId(comps.get("TAX").getId())
                                .fixedValue(BigDecimal.valueOf(5.0))
                                .effectiveDate(LocalDate.now())
                                .build()
                )).build(); // FEE is omitted, should be deleted

        mockMvc.perform(patch(PRODUCT_API_BASE + "/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncRequest)))
                .andExpect(status().isOk());

        txHelper.doInTransaction(() -> {
            List<ProductPricingLink> links = pricingLinkRepository.findByProductId(productId);
            assertThat(links).hasSize(1);
            assertThat(links.getFirst().getPricingComponent().getName()).isEqualTo("Tax");
        });
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldAllowDormantPricingLinkWithoutDates() throws Exception {
        Long productId = createDraftProductInDb("Dormant Test");
        PricingComponent fee = txHelper.doInTransaction(() ->
                pricingComponentRepository.save(PricingComponent.builder()
                        .name("Dormant Fee")
                        .code("DORM1")
                        .type(PricingComponent.ComponentType.FEE)
                        .build())
        );

        // Request with NO dates
        ProductRequest request = ProductRequest.builder()
                .pricing(List.of(ProductPricingDto.builder()
                        .pricingComponentId(fee.getId())
                        .fixedValue(BigDecimal.TEN)
                        .build()))
                .build();

        mockMvc.perform(patch(PRODUCT_API_BASE + "/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        txHelper.doInTransaction(() -> {
            ProductPricingLink link = pricingLinkRepository.findByProductId(productId).get(0);
            assertThat(link.getEffectiveDate()).isNull();
            assertThat(link.getExpiryDate()).isNull();
        });
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    @DisplayName("Sync - Should clear all links when empty lists are provided")
    void shouldClearAllLinksWhenListsAreEmpty() throws Exception {
        Long productId = createDraftProductInDb("Clearance Product");
        txHelper.doInTransaction(() -> {
            Product product = productRepository.getReferenceById(productId);
            FeatureComponent fc = featureComponentRepository.save(FeatureComponent.builder().name("X").code("X").dataType(FeatureComponent.DataType.STRING).build());
            featureLinkRepository.save(ProductFeatureLink.builder().product(product).featureComponent(fc).featureValue("Y").build());
        });

        ProductRequest clearRequest = ProductRequest.builder()
                .features(List.of())
                .pricing(List.of())
                .build();

        mockMvc.perform(patch(PRODUCT_API_BASE + "/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clearRequest)))
                .andExpect(status().isOk());

        txHelper.doInTransaction(() -> {
            assertThat(featureLinkRepository.findByProductId(productId)).isEmpty();
            assertThat(pricingLinkRepository.findByProductId(productId)).isEmpty();
        });
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn404OnInvalidComponentId() throws Exception {
        Long productId = createDraftProductInDb("Invalid Ref Product");
        ProductRequest badRequest = ProductRequest.builder()
                .features(List.of(ProductFeatureDto.builder().featureComponentId(9999L).featureValue("Err").build()))
                .build();

        mockMvc.perform(patch(PRODUCT_API_BASE + "/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Feature Component not found")));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn400WhenEffectiveDateIsInPast() throws Exception {
        // Setup: Create a draft product
        Product product = txHelper.doInTransaction(() ->
                txHelper.createValidProduct("Past Date Test",
                        "SAVINGS", VersionableEntity.EntityStatus.DRAFT)
        );

        // Create request with a date from yesterday
        ProductRequest request = ProductRequest.builder()
                .pricing(List.of(ProductPricingDto.builder()
                        .pricingComponentId(EXISTING_PRICING_COMP_ID)
                        .effectiveDate(LocalDate.now().minusDays(1)) // <--- THE VIOLATION
                        .build()))
                .build();

        // Act & Assert
        mockMvc.perform(patch(PRODUCT_API_BASE + "/{id}", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()) // 400
                .andExpect(jsonPath("$.message").value("Effective date cannot be in the past."));
    }
}