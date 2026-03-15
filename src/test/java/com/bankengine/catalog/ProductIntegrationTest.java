package com.bankengine.catalog;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.dto.ProductRequest;
import com.bankengine.catalog.dto.VersionRequest;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Focuses on Product Lifecycle, Metadata, and Security.
 */
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
    private TestTransactionHelper txHelper;

    private static Long EXISTING_PRODUCT_TYPE_ID;
    private final String PRODUCT_API_BASE = "/api/v1/products";

    private static final String ADMIN_ROLE = "PIT_CATALOG_ADMIN";
    private static final String READER_ROLE = "PIT_CATALOG_READER";
    private static final String UNAUTHORIZED_ROLE = "PIT_UNAUTHORIZED_ROLE";

    @BeforeAll
    static void setupRoles(@Autowired TestTransactionHelper txHelperStatic,
                           @Autowired ProductTypeRepository productTypeRepoStatic,
                           @Autowired ProductRepository productRepoStatic,
                           @Autowired ProductFeatureLinkRepository featureLinkRepoStatic,
                           @Autowired ProductPricingLinkRepository pricingLinkRepoStatic) {

        seedBaseRoles(txHelperStatic, Map.of(
                ADMIN_ROLE, Set.of("catalog:product:create", "catalog:product:read", "catalog:product:update", "catalog:product:activate", "catalog:product:deactivate", "catalog:product:version"),
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
                pt.setCode("BCT");
                pt.setBankId(TEST_BANK_ID);
                EXISTING_PRODUCT_TYPE_ID = productTypeRepoStatic.save(pt).getId();
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
        entityManager.clear();
    }

    // --- Helpers ---

    private ProductRequest.ProductRequestBuilder defaultRequestBuilder() {
        return ProductRequest.builder()
                .name("Standard Product Name")
                .code("CODE_" + UUID.randomUUID().toString().substring(0, 8))
                .productTypeCode("BCT")
                .category("RETAIL")
                .activationDate(LocalDate.now().plusDays(1))
                .expiryDate(LocalDate.now().plusYears(1));
    }

    private Long createProductViaApi(ProductRequest request) throws Exception {
        String json = mockMvc.perform(postWithCsrf(PRODUCT_API_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).path("id").asLong();
    }

    private Long createProductInStatus(String targetStatus) throws Exception {
        // Use a transaction/repository directly for setup to avoid 403s on the setup phase
        return txHelper.doInTransaction(() -> {
            Product p = Product.builder()
                    .name("Standard Product")
                    .code("CODE_" + UUID.randomUUID().toString().substring(0, 8))
                    .bankId(TEST_BANK_ID)
                    .productType(productTypeRepository.getReferenceById(EXISTING_PRODUCT_TYPE_ID))
                    .category("RETAIL")
                    .status(VersionableEntity.EntityStatus.valueOf(targetStatus))
                    .expiryDate(LocalDate.now().plusYears(1))
                    .version(1)
                    .build();
            return productRepository.save(p).getId();
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
    // 2. CREATE & READ
    // =================================================================

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void shouldReturn403WhenCreatingProductWithoutPermission() throws Exception {
        mockMvc.perform(postWithCsrf(PRODUCT_API_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultRequestBuilder().build())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldCreateProductAsDraftByDefault() throws Exception {
        ProductRequest request = defaultRequestBuilder().build();
        mockMvc.perform(postWithCsrf(PRODUCT_API_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Standard Product Name"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn404WhenCreatingProductWithNonExistentProductType() throws Exception {
        ProductRequest requestDto = defaultRequestBuilder().productTypeCode("INVALID_TYPE").build();
        mockMvc.perform(postWithCsrf(PRODUCT_API_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn404WhenGettingNonExistentProduct() throws Exception {
        mockMvc.perform(get(PRODUCT_API_BASE + "/99999")).andExpect(status().isNotFound());
    }

    // =================================================================
    // 3. METADATA UPDATE
    // =================================================================

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldUpdateMetadataWhenProductIsDraft() throws Exception {
        Long productId = createProductInStatus("DRAFT");
        ProductRequest updateDto = defaultRequestBuilder().name("Updated Name").build();

        mockMvc.perform(patchWithCsrf(PRODUCT_API_BASE + "/{productId}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn409WhenUpdatingMetadataForActiveProduct() throws Exception {
        Long productId = createProductInStatus("ACTIVE");
        ProductRequest updateDto = defaultRequestBuilder().name("Illegal Update").build();

        mockMvc.perform(patchWithCsrf(PRODUCT_API_BASE + "/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void shouldReturn403WhenUpdatingProductWithoutPermission() throws Exception {
        Long productId = txHelper.createProductInDb("Secured", EXISTING_PRODUCT_TYPE_ID, "RETAIL");
        mockMvc.perform(patchWithCsrf(PRODUCT_API_BASE + "/{id}", productId)
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
        Long productId = createProductInStatus("DRAFT");
        mockMvc.perform(postWithCsrf(PRODUCT_API_BASE + "/{id}/activate", productId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldActivateDraftProduct() throws Exception {
        Long productId = createProductInStatus("DRAFT");
        LocalDate actDate = LocalDate.now().plusDays(2);

        mockMvc.perform(postWithCsrf(PRODUCT_API_BASE + "/{id}/activate", productId)
                        .param("activationDate", actDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.activationDate").value(actDate.toString()));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldDeactivateActiveProduct() throws Exception {
        Long productId = createProductInStatus("ACTIVE");
        mockMvc.perform(postWithCsrf(PRODUCT_API_BASE + "/{id}/deactivate", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldExtendProductExpirationDate() throws Exception {
        // 1. Arrange: Create an ACTIVE product
        Long productId = createProductInStatus("ACTIVE");
        LocalDate newExp = LocalDate.now().plusYears(2);

        // 2. Act: Call the dedicated extension endpoint
        mockMvc.perform(postWithCsrf(PRODUCT_API_BASE + "/{id}/extend-expiry", productId)
                        .param("newExpiryDate", newExp.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiryDate").value(newExp.toString()));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn409WhenExtendingArchivedProduct() throws Exception {
        // 1. Arrange: Create an ARCHIVED product
        Long productId = createProductInStatus("ARCHIVED");
        LocalDate newExp = LocalDate.now().plusYears(2);

        // 2. Act & Assert: Should fail with 409 (or 400 depending on your Global Exception Handler)
        mockMvc.perform(postWithCsrf(PRODUCT_API_BASE + "/{id}/extend-expiry", productId)
                        .param("newExpiryDate", newExp.toString()))
                .andExpect(status().isConflict());
    }

    // =================================================================
    // 5. VERSIONING
    // =================================================================

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldCloneFeaturesAndPricingWhenCreatingNewVersion() throws Exception {
        // 1. Setup the active product
        Long oldProductId = createProductInStatus("ACTIVE");

        // We must fetch the product to get the auto-generated code from the helper
        String originalCode = txHelper.doInTransaction(() ->
                productRepository.findById(oldProductId).orElseThrow().getCode()
        );

        txHelper.doInTransaction(() -> {
            Product oldProduct = productRepository.findById(oldProductId).orElseThrow();
            FeatureComponent fc = featureComponentRepository.save(FeatureComponent.builder()
                    .name("Premium Support")
                    .code("SUP_" + UUID.randomUUID())
                    .dataType(FeatureComponent.DataType.STRING).build());
            featureLinkRepository.save(ProductFeatureLink.builder()
                    .product(oldProduct).featureComponent(fc).featureValue("Included").build());

            PricingComponent pc = pricingComponentRepository.save(PricingComponent.builder()
                    .name("Monthly Fee")
                    .code("FEE_" + UUID.randomUUID())
                    .type(PricingComponent.ComponentType.FEE).build());
            pricingLinkRepository.save(ProductPricingLink.builder()
                    .product(oldProduct).pricingComponent(pc).fixedValue(BigDecimal.TEN).build());
        });

        // 2. Act: Use the ORIGINAL code to trigger a REVISION (Archiving)
        VersionRequest versionDto = new VersionRequest("Checking V2", originalCode, LocalDate.now().plusMonths(1), null);

        String response = mockMvc.perform(postWithCsrf(PRODUCT_API_BASE + "/{id}/create-new-version", oldProductId)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(versionDto)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        Long newId = objectMapper.readTree(response).get("id").asLong();

        // 3. Assert: Verify the new version has cloned associations
        mockMvc.perform(get(PRODUCT_API_BASE + "/{id}", newId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()").value(1))
                .andExpect(jsonPath("$.pricing.length()").value(1))
                .andExpect(jsonPath("$.features[0].featureName").value("Premium Support"))
                .andExpect(jsonPath("$.pricing[0].pricingComponentName").value("Monthly Fee"));

        // 4. Assert: Verify the original is now ARCHIVED
        mockMvc.perform(get(PRODUCT_API_BASE + "/{id}", oldProductId))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }
}