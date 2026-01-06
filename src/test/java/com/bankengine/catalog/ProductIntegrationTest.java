package com.bankengine.catalog;

import com.bankengine.auth.config.test.WithMockRole;
import com.bankengine.auth.security.BankContextHolder;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
public class ProductIntegrationTest extends AbstractIntegrationTest {

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
    private EntityManager entityManager;
    @Autowired
    private TestTransactionHelper txHelper;

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

        BankContextHolder.setBankId(TEST_BANK_ID);
        try {
            txHelperStatic.doInTransaction(() -> {
                featureLinkRepoStatic.deleteAllInBatch();
                pricingLinkRepoStatic.deleteAllInBatch();
                productRepoStatic.deleteAllInBatch();
                productTypeRepoStatic.deleteAllInBatch();
            });

            txHelperStatic.createRoleInDb(ADMIN_ROLE, Set.of("catalog:product:create", "catalog:product:read", "catalog:product:update", "catalog:product:activate", "catalog:product:deactivate"));
            txHelperStatic.createRoleInDb(READER_ROLE, Set.of("catalog:product:read"));
            txHelperStatic.createRoleInDb(UNAUTHORIZED_ROLE, Set.of("some:other:permission"));

            txHelperStatic.doInTransaction(() -> {
                ProductType pt = new ProductType();
                pt.setName("Base Checking Type");
                pt.setBankId(TEST_BANK_ID);
                EXISTING_PRODUCT_TYPE_ID = productTypeRepoStatic.save(pt).getId();
            });
        } finally {
            BankContextHolder.clear();
        }
    }

    @AfterEach
    void tearDown() {
        txHelper.doInTransaction(() -> {
            featureLinkRepository.deleteAllInBatch();
            pricingLinkRepository.deleteAllInBatch();
            productRepository.deleteAllInBatch();
        });
        entityManager.clear();
    }

    // =================================================================
    // HELPER METHODS
    // =================================================================

    private ProductRequest.ProductRequestBuilder defaultRequestBuilder() {
        return ProductRequest.builder()
                .name("Standard Product Name")
                .bankId(TEST_BANK_ID)
                .productTypeId(EXISTING_PRODUCT_TYPE_ID)
                .effectiveDate(LocalDate.now().plusDays(1))
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

    private Long createProductType(String name) {
        ProductType pt = new ProductType();
        pt.setName(name);
        pt.setBankId(TEST_BANK_ID);
        return productTypeRepository.save(pt).getId();
    }

    private void setupMultipleProductsForSearch() throws Exception {
        createProductViaApi(defaultRequestBuilder().status("DRAFT").name("Draft Product A").build());
        createProductViaApi(defaultRequestBuilder().status("ACTIVE").name("Active Checking").effectiveDate(LocalDate.now().minusDays(1)).build());

        Long typeBId = createProductType("Type B");
        createProductViaApi(defaultRequestBuilder().status("INACTIVE").name("Inactive Savings").productTypeId(typeBId).build());
        createProductViaApi(defaultRequestBuilder().status("ACTIVE").name("Premium Card").build());

        BankContextHolder.setBankId(OTHER_BANK_ID);
        try {
            Long foreignTypeId = createProductType("Foreign Type X");
            createProductViaApi(defaultRequestBuilder().bankId(OTHER_BANK_ID).name("Foreign Product 1").productTypeId(foreignTypeId).status("ACTIVE").build());
            createProductViaApi(defaultRequestBuilder().bankId(OTHER_BANK_ID).name("Foreign Product 2").productTypeId(foreignTypeId).status("DRAFT").build());
        } finally {
            BankContextHolder.clear();
        }
    }

    // =================================================================
    // SECURITY & AUTHENTICATION TESTS
    // =================================================================

    @Test
    void shouldReturn401WhenAccessingSecureEndpointWithoutToken() throws Exception {
        mockMvc.perform(get(PRODUCT_API_BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn200WhenAccessingSecureEndpointWithToken() throws Exception {
        txHelper.createProductInDb("Security Test", EXISTING_PRODUCT_TYPE_ID);
        mockMvc.perform(get(PRODUCT_API_BASE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void shouldReturn403WhenReadingProductsWithoutPermission() throws Exception {
        mockMvc.perform(get(PRODUCT_API_BASE)).andExpect(status().isForbidden());
    }

    // =================================================================
    // CREATE (POST) & READ (GET) TESTS
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
    // METADATA UPDATE (PUT) TESTS
    // =================================================================

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldUpdateMetadataWhenProductIsDraft() throws Exception {
        Long productId = createProductViaApi("DRAFT");
        ProductRequest updateDto = defaultRequestBuilder().name("Updated Name").bankId("BC-002").build();

        mockMvc.perform(put(PRODUCT_API_BASE + "/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn400WhenUpdatingMetadataForActiveProduct() throws Exception {
        Long productId = createProductViaApi("ACTIVE");
        ProductRequest updateDto = defaultRequestBuilder().status("ACTIVE").build();

        mockMvc.perform(put(PRODUCT_API_BASE + "/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void shouldReturn403WhenUpdatingProductWithoutPermission() throws Exception {
        Long productId = txHelper.createProductInDb("Secured", EXISTING_PRODUCT_TYPE_ID);
        mockMvc.perform(put(PRODUCT_API_BASE + "/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultRequestBuilder().build())))
                .andExpect(status().isForbidden());
    }

    // =================================================================
    // LIFECYCLE ACTIONS (ACTIVATE/DEACTIVATE/EXTEND)
    // =================================================================

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void shouldReturn403WhenActivatingProductWithoutPermission() throws Exception {
        Long productId = txHelper.createProductInDb("Test", EXISTING_PRODUCT_TYPE_ID);
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
        ProductExpirationRequest dto = new ProductExpirationRequest();
        dto.setExpirationDate(newExp);

        mockMvc.perform(put(PRODUCT_API_BASE + "/{id}/expiration", productId)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expirationDate").value(newExp.toString()));
    }

    // =================================================================
    // FEATURES & PRICING SYNC TESTS
    // =================================================================

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldSyncProductFeatures() throws Exception {
        Long productId = createProductViaApi("DRAFT");

        FeatureComponent feature = new FeatureComponent("Feature 1", FeatureComponent.DataType.STRING);
        feature.setBankId(TEST_BANK_ID);
        feature = featureComponentRepository.save(feature);

        ProductFeatureRequest featureReq = ProductFeatureRequest.builder()
                .featureComponentId(feature.getId())
                .featureValue("Value 1")
                .build();

        mockMvc.perform(put(PRODUCT_API_BASE + "/{id}/features", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(featureReq))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features[0].featureName").value("Feature 1"));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldSyncProductPricing() throws Exception {
        Long productId = createProductViaApi("DRAFT");

        PricingComponent pricing = new PricingComponent("Pricing 1", PricingComponent.ComponentType.FEE);
        pricing.setBankId(TEST_BANK_ID);
        pricing = pricingComponentRepository.save(pricing);

        ProductPricingRequest pricingReq = ProductPricingRequest.builder()
                .pricingComponentId(pricing.getId())
                .context("DEFAULT")
                .build();

        mockMvc.perform(put(PRODUCT_API_BASE + "/{id}/pricing", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(pricingReq))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricing.length()").value(1))
                .andExpect(jsonPath("$.pricing[0].pricingComponentName").value("Pricing 1"));
    }

    // =================================================================
    // VERSIONING TESTS
    // =================================================================

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldCloneFeaturesAndPricingWhenCreatingNewVersion() throws Exception {
        Long oldProductId = createProductViaApi("ACTIVE");
        Product oldProduct = productRepository.findById(oldProductId).orElseThrow();

        // 1. Setup Feature
        FeatureComponent feature = new FeatureComponent("Premium Support", FeatureComponent.DataType.STRING);
        feature.setBankId(TEST_BANK_ID);
        feature = featureComponentRepository.save(feature);

        ProductFeatureLink featureLink = new ProductFeatureLink(oldProduct, feature, "Included");
        featureLink.setBankId(TEST_BANK_ID);
        featureLinkRepository.save(featureLink);

        // 2. Setup Pricing (THIS WAS MISSING THE LINK)
        PricingComponent pricing = new PricingComponent("Monthly Fee", PricingComponent.ComponentType.FEE);
        pricing.setBankId(TEST_BANK_ID);
        pricing = pricingComponentRepository.save(pricing);

        // Create the link for the old product so the service has something to clone
        ProductPricingLink pricingLink = new ProductPricingLink();
        pricingLink.setProduct(oldProduct);
        pricingLink.setPricingComponent(pricing);
        pricingLink.setContext("Retail");
        pricingLink.setBankId(TEST_BANK_ID);
        pricingLink.setUseRulesEngine(false);
        pricingLinkRepository.save(pricingLink);

        // Sync to DB
        entityManager.flush();
        entityManager.clear();

        // 3. Execute Versioning
        LocalDate newEffectiveDate = LocalDate.now().plusMonths(3);
        NewProductVersionRequest versionDto = new NewProductVersionRequest("Checking Account V2", newEffectiveDate);

        String responseJson = mockMvc.perform(post(PRODUCT_API_BASE + "/{id}/new-version", oldProductId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(versionDto)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long newProductId = objectMapper.readValue(responseJson, ProductResponse.class).getId();

        // 4. Assertions
        mockMvc.perform(get(PRODUCT_API_BASE + "/{id}", newProductId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()").value(1))
                .andExpect(jsonPath("$.pricing.length()").value(1))
                .andExpect(jsonPath("$.features[0].featureName").value("Premium Support"))
                .andExpect(jsonPath("$.features[0].featureValue").value("Included"))
                .andExpect(jsonPath("$.pricing[0].pricingComponentName").value("Monthly Fee"))
                .andExpect(jsonPath("$.pricing[0].context").value("Retail"));

        // 5. Verify Archival
        mockMvc.perform(get(PRODUCT_API_BASE + "/{id}", oldProductId))
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.expirationDate").value(newEffectiveDate.minusDays(1).toString()));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldCreateNewVersionWhenActiveProductIsVersioned() throws Exception {
        Long oldId = createProductViaApi("ACTIVE");
        NewProductVersionRequest dto = new NewProductVersionRequest("V2", LocalDate.now().plusMonths(1));

        mockMvc.perform(post(PRODUCT_API_BASE + "/{id}/new-version", oldId)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(PRODUCT_API_BASE + "/{id}", oldId))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn400WhenVersioningDraftProduct() throws Exception {
        Long productId = createProductViaApi("DRAFT");
        NewProductVersionRequest dto = new NewProductVersionRequest("Fail", LocalDate.now());
        mockMvc.perform(post(PRODUCT_API_BASE + "/{id}/new-version", productId)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    // =================================================================
    // SEARCH & FILTERING TESTS
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
        Long typeCId = createProductType("Type C");
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