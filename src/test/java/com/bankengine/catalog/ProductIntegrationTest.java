package com.bankengine.catalog;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.dto.*;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.FeatureComponentRepository;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ProductIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductTypeRepository productTypeRepository;
    @Autowired private FeatureComponentRepository featureComponentRepository;
    @Autowired private PricingComponentRepository pricingComponentRepository;
    @Autowired private ProductFeatureLinkRepository featureLinkRepository;
    @Autowired private ProductPricingLinkRepository pricingLinkRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private TestTransactionHelper txHelper;

    private static Long EXISTING_PRODUCT_TYPE_ID;
    private final String PRODUCT_API_BASE = "/api/v1/products";

    public static final String ROLE_PREFIX = "PIT_";
    private static final String ADMIN_ROLE = ROLE_PREFIX + "CATALOG_ADMIN";
    private static final String READER_ROLE = ROLE_PREFIX + "CATALOG_READER";
    private static final String UNAUTHORIZED_ROLE = ROLE_PREFIX + "UNAUTHORIZED_ROLE";

    @BeforeAll
    static void setupRoles(@Autowired TestTransactionHelper txHelperStatic,
                           @Autowired ProductTypeRepository productTypeRepoStatic,
                           @Autowired ProductRepository productRepoStatic,
                           @Autowired ProductFeatureLinkRepository featureLinkRepoStatic,
                           @Autowired ProductPricingLinkRepository pricingLinkRepoStatic) {

        seedBaseRoles(txHelperStatic, Map.of(
            ADMIN_ROLE, Set.of("catalog:product:create", "catalog:product:read", "catalog:product:update", "catalog:product:activate", "catalog:product:deactivate"),
            READER_ROLE, Set.of("catalog:product:read"),
            UNAUTHORIZED_ROLE, Set.of("some:other:permission")
        ));

        try {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            txHelperStatic.doInTransaction(() -> {
                featureLinkRepoStatic.deleteAllInBatch();
                pricingLinkRepoStatic.deleteAllInBatch();
                productRepoStatic.deleteAllInBatch();
                productTypeRepoStatic.deleteAllInBatch();

                ProductType pt = new ProductType();
                pt.setName("Base Checking Type");
                EXISTING_PRODUCT_TYPE_ID = productTypeRepoStatic.save(pt).getId();
            });
        } finally {
            TenantContextHolder.clear();
        }
    }

    @AfterEach
    void tearDown() {
        txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            featureLinkRepository.deleteAllInBatch();
            pricingLinkRepository.deleteAllInBatch();
            productRepository.deleteAllInBatch();
        });
        entityManager.clear();
    }

    // --- Helpers ---

    private ProductRequest.ProductRequestBuilder defaultRequestBuilder() {
        return ProductRequest.builder()
                .name("Standard Product Name")
                .productTypeId(EXISTING_PRODUCT_TYPE_ID)
                .effectiveDate(LocalDate.now().plusDays(1))
                .category("RETAIL")
                .status("DRAFT");
    }

    private Long createProductViaApi(ProductRequest request) throws Exception {
        String json = mockMvc.perform(post(PRODUCT_API_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).path("id").asLong();
    }

    private Long createProductViaApi(String status) throws Exception {
        LocalDate date = status.equals("ACTIVE") ? LocalDate.now().minusDays(1) : LocalDate.now().plusDays(1);
        return createProductViaApi(defaultRequestBuilder().status(status).name(status + " Product").effectiveDate(date).build());
    }

    private void setupMultipleProductsForSearch() throws Exception {
        // 1. Create standard products via API
        createProductViaApi(defaultRequestBuilder().status("DRAFT").name("Draft Product A").build());
        createProductViaApi(defaultRequestBuilder().status("ACTIVE").name("Active Checking").effectiveDate(LocalDate.now().minusDays(1)).build());

        // 2. Setup "Type B" using find-or-create to avoid Unique Constraint violations
        Long typeBId = txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            return productTypeRepository.findByName("Type B")
                    .map(ProductType::getId)
                    .orElseGet(() -> {
                        ProductType ptB = new ProductType();
                        ptB.setName("Type B");
                        return productTypeRepository.save(ptB).getId();
                    });
        });

        // 3. Create products for Type B
        createProductViaApi(defaultRequestBuilder()
                .status("INACTIVE")
                .name("Inactive Savings")
                .productTypeId(typeBId)
                .build());

        createProductViaApi(defaultRequestBuilder()
                .status("ACTIVE")
                .name("Premium Card")
                .build());

        // 4. Setup Foreign Bank Data (Find or Create)
        txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(OTHER_BANK_ID);
            if (productTypeRepository.findByName("Foreign Type X").isEmpty()) {
                ProductType ptForeign = new ProductType();
                ptForeign.setName("Foreign Type X");
                productTypeRepository.save(ptForeign);
            }
        });
    }

    // =================================================================
    // 1. SECURITY & AUTHENTICATION
    // =================================================================

    @Test
    void shouldReturn401WhenAccessingSecureEndpointWithoutToken() throws Exception {
        mockMvc.perform(get(PRODUCT_API_BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn200WhenAccessingSecureEndpointWithToken() throws Exception {
        txHelper.createProductInDb("Security Test", EXISTING_PRODUCT_TYPE_ID, "RETAIL");
        mockMvc.perform(get(PRODUCT_API_BASE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void shouldReturn403WhenReadingProductsWithoutPermission() throws Exception {
        mockMvc.perform(get(PRODUCT_API_BASE)).andExpect(status().isForbidden());
    }

    // =================================================================
    // 2. CREATE (POST) & READ (GET)
    // =================================================================

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void shouldReturn403WhenCreatingProductWithoutPermission() throws Exception {
        mockMvc.perform(post(PRODUCT_API_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultRequestBuilder().build())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldCreateProductAndReturn201() throws Exception {
        ProductRequest request = defaultRequestBuilder().build();
        mockMvc.perform(post(PRODUCT_API_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Standard Product Name"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn404WhenCreatingProductWithNonExistentProductType() throws Exception {
        ProductRequest requestDto = defaultRequestBuilder().productTypeId(99999L).build();
        mockMvc.perform(post(PRODUCT_API_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn404WhenGettingNonExistentProduct() throws Exception {
        mockMvc.perform(get(PRODUCT_API_BASE + "/99999")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn200AndPageableResponse() throws Exception {
        createProductViaApi("DRAFT");
        mockMvc.perform(get(PRODUCT_API_BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // =================================================================
    // 3. METADATA UPDATE (PUT)
    // =================================================================

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldUpdateMetadataWhenProductIsDraft() throws Exception {
        Long productId = createProductViaApi("DRAFT");
        ProductRequest updateDto = defaultRequestBuilder().name("Updated Name").build();

        mockMvc.perform(put(PRODUCT_API_BASE + "/{productId}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn409WhenUpdatingMetadataForActiveProduct() throws Exception {
        Long productId = createProductViaApi("ACTIVE");
        ProductRequest updateDto = defaultRequestBuilder().status("ACTIVE").build();

        mockMvc.perform(put(PRODUCT_API_BASE + "/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void shouldReturn403WhenUpdatingProductWithoutPermission() throws Exception {
        Long productId = txHelper.createProductInDb("Secured", EXISTING_PRODUCT_TYPE_ID, "RETAIL");
        mockMvc.perform(put(PRODUCT_API_BASE + "/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultRequestBuilder().build())))
                .andExpect(status().isForbidden());
    }

    // =================================================================
    // 4. LIFECYCLE ACTIONS
    // =================================================================

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void shouldReturn403WhenActivatingProductWithoutPermission() throws Exception {
        Long productId = txHelper.createProductInDb("Test", EXISTING_PRODUCT_TYPE_ID, "RETAIL");
        mockMvc.perform(post(PRODUCT_API_BASE + "/{id}/activate", productId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldActivateDraftProductAndSetStatusToActive() throws Exception {
        Long productId = createProductViaApi("DRAFT");
        LocalDate actDate = LocalDate.now().plusDays(5);

        mockMvc.perform(post(PRODUCT_API_BASE + "/{id}/activate", productId)
                        .param("effectiveDate", actDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.effectiveDate").value(actDate.toString()));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldDeactivateActiveProductAndSetStatusToInactive() throws Exception {
        Long productId = createProductViaApi("ACTIVE");
        mockMvc.perform(post(PRODUCT_API_BASE + "/{id}/deactivate", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldExtendProductExpirationDate() throws Exception {
        Long productId = createProductViaApi("ACTIVE");
        LocalDate newExp = LocalDate.now().plusYears(1);
        Map<String, Object> payload = Map.of("expirationDate", newExp.toString());

        mockMvc.perform(put(PRODUCT_API_BASE + "/{id}/expiration", productId)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expirationDate").value(newExp.toString()));
    }

    // =================================================================
    // 5. FEATURES & PRICING SYNC
    // =================================================================

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldSyncProductFeatures() throws Exception {
        Long productId = createProductViaApi("DRAFT");
        txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            FeatureComponent feature = new FeatureComponent("Feature 1", FeatureComponent.DataType.STRING);
            featureComponentRepository.save(feature);
        });

        FeatureComponent fc = txHelper.doInTransaction(() -> featureComponentRepository.findByName("Feature 1").get());
        ProductFeature req = ProductFeature.builder().featureComponentId(fc.getId()).featureValue("Value 1").build();

        mockMvc.perform(put(PRODUCT_API_BASE + "/{id}/features", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(req))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features[0].featureName").value("Feature 1"));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldSyncProductPricing() throws Exception {
        Long productId = createProductViaApi("DRAFT");
        txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            PricingComponent pricing = new PricingComponent("Pricing 1", PricingComponent.ComponentType.FEE);
            pricingComponentRepository.save(pricing);
        });

        PricingComponent pc = txHelper.doInTransaction(() -> pricingComponentRepository.findByName("Pricing 1").get());
        ProductPricing req = ProductPricing.builder().pricingComponentId(pc.getId()).build();

        mockMvc.perform(put(PRODUCT_API_BASE + "/{id}/pricing", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(req))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricing.length()").value(1))
                .andExpect(jsonPath("$.pricing[0].pricingComponentName").value("Pricing 1"));
    }

    // =================================================================
    // 6. VERSIONING
    // =================================================================

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldCloneFeaturesAndPricingWhenCreatingNewVersion() throws Exception {
        Long oldProductId = createProductViaApi("ACTIVE");

        txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            Product oldProduct = productRepository.findById(oldProductId).orElseThrow();

            FeatureComponent fc = featureComponentRepository.save(new FeatureComponent("Premium Support", FeatureComponent.DataType.STRING));
            featureLinkRepository.save(new ProductFeatureLink(oldProduct, fc, "Included"));

            PricingComponent pc = pricingComponentRepository.save(new PricingComponent("Monthly Fee", PricingComponent.ComponentType.FEE));
            ProductPricingLink pLink = new ProductPricingLink();
            pLink.setProduct(oldProduct); pLink.setPricingComponent(pc);
            pLink.setUseRulesEngine(false);
            pricingLinkRepository.save(pLink);
        });

        LocalDate newDate = LocalDate.now().plusMonths(3);
        ProductVersionRequest versionDto = new ProductVersionRequest("Checking Account V2", newDate);

        String response = mockMvc.perform(post(PRODUCT_API_BASE + "/{id}/new-version", oldProductId)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(versionDto)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        Long newId = objectMapper.readValue(response, ProductResponse.class).getId();

        mockMvc.perform(get(PRODUCT_API_BASE + "/{id}", newId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()").value(1))
                .andExpect(jsonPath("$.pricing.length()").value(1))
                .andExpect(jsonPath("$.features[0].featureName").value("Premium Support"))
                .andExpect(jsonPath("$.features[0].featureValue").value("Included"))
                .andExpect(jsonPath("$.pricing[0].pricingComponentName").value("Monthly Fee"));

        // 5. Verify Archival
        mockMvc.perform(get(PRODUCT_API_BASE + "/{id}", oldProductId))
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.expirationDate").value(newDate.minusDays(1).toString()));
        // Verify Archival of old
        txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            Product old = productRepository.findById(oldProductId).orElseThrow();
            assertThat(old.getStatus()).isEqualTo("ARCHIVED");
        });

    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldCreateNewVersionWhenActiveProductIsVersioned() throws Exception {
        Long oldId = createProductViaApi("ACTIVE");
        ProductVersionRequest dto = new ProductVersionRequest("V2", LocalDate.now().plusMonths(1));
        mockMvc.perform(post(PRODUCT_API_BASE + "/{id}/new-version", oldId)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(PRODUCT_API_BASE + "/{id}", oldId)).andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn409WhenVersioningDraftProduct() throws Exception {
        Long id = createProductViaApi("DRAFT");
        ProductVersionRequest dto = new ProductVersionRequest("Fail", LocalDate.now());
        mockMvc.perform(post(PRODUCT_API_BASE + "/{id}/new-version", id)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());
    }

    // =================================================================
    // 7. SEARCH & FILTERING
    // =================================================================

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturnAllProductsWhenNoFilterCriteriaAreProvided() throws Exception {
        setupMultipleProductsForSearch();
        mockMvc.perform(get(PRODUCT_API_BASE)).andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldFilterProductsByStatusAndName() throws Exception {
        setupMultipleProductsForSearch();
        mockMvc.perform(get(PRODUCT_API_BASE).param("status", "ACTIVE").param("name", "Checking"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Active Checking"));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldFilterProductsByBankIdAndProductType() throws Exception {
        Long typeCId = txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            ProductType pt = new ProductType(); pt.setName("Type C");
            return productTypeRepository.save(pt).getId();
        });
        createProductViaApi(defaultRequestBuilder().name("Target").productTypeId(typeCId).build());

        mockMvc.perform(get(PRODUCT_API_BASE).param("bankId", TEST_BANK_ID).param("productTypeId", typeCId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturnZeroProductsWhenNoCriteriaMatch() throws Exception {
        setupMultipleProductsForSearch();
        mockMvc.perform(get(PRODUCT_API_BASE).param("status", "ARCHIVED"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldFilterProductsByEffectiveDateRange() throws Exception {
        createProductViaApi(defaultRequestBuilder().name("Future").effectiveDate(LocalDate.now().plusDays(10)).build());
        createProductViaApi(defaultRequestBuilder().name("Past").effectiveDate(LocalDate.now().minusDays(10)).build());

        mockMvc.perform(get(PRODUCT_API_BASE).param("effectiveDateFrom", LocalDate.now().plusDays(1).toString()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Future"));
    }
}