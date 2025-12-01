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
import com.fasterxml.jackson.databind.JsonNode;
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

    // --- Role Constants ---
    public static final String ROLE_PREFIX = "PIT_";
    private static final String ADMIN_ROLE = ROLE_PREFIX + "CATALOG_ADMIN";
    private static final String READER_ROLE = ROLE_PREFIX + "CATALOG_READER";
    private static final String UNAUTHORIZED_ROLE = ROLE_PREFIX + "UNAUTHORIZED_ROLE";

    // =================================================================
    // SETUP AND TEARDOWN
    // =================================================================

    /**
     * Set up committed, required roles and the base ProductType once before all tests run.
     */
    @BeforeAll
    static void setupRoles(@Autowired TestTransactionHelper txHelperStatic,
                           @Autowired ProductTypeRepository productTypeRepoStatic,
                           @Autowired ProductRepository productRepoStatic,
                           @Autowired ProductFeatureLinkRepository featureLinkRepoStatic,
                           @Autowired ProductPricingLinkRepository pricingLinkRepoStatic) {

        // CRITICAL: Set the thread-local bank context for the static method's thread
        // This relies on TEST_BANK_ID being public/protected in AbstractIntegrationTest.
        BankContextHolder.setBankId(TEST_BANK_ID);

        try {
            // 1. AGGRESSIVE GLOBAL CLEANUP
            txHelperStatic.doInTransaction(() -> {
                featureLinkRepoStatic.deleteAllInBatch();
                pricingLinkRepoStatic.deleteAllInBatch();
                productRepoStatic.deleteAllInBatch();
                productTypeRepoStatic.deleteAllInBatch();
            });
            txHelperStatic.flushAndClear();

            // 2. Setup Roles and Base ProductType (Committed Transaction)
            Set<String> adminAuths = Set.of(
                    "catalog:product:create",
                    "catalog:product:read",
                    "catalog:product:update",
                    "catalog:product:activate",
                    "catalog:product:deactivate"
            );
            Set<String> readerAuths = Set.of("catalog:product:read");
            Set<String> unauthorizedAuths = Set.of("some:other:permission");

            txHelperStatic.createRoleInDb(ADMIN_ROLE, adminAuths);
            txHelperStatic.createRoleInDb(READER_ROLE, readerAuths);
            txHelperStatic.createRoleInDb(UNAUTHORIZED_ROLE, unauthorizedAuths);

            txHelperStatic.doInTransaction(() -> {
                ProductType productType = productTypeRepoStatic.findByName("Base Checking Type")
                        .orElseGet(() -> {
                            ProductType newType = new ProductType();
                            newType.setName("Base Checking Type");
                            newType.setBankId(TEST_BANK_ID);
                            return productTypeRepoStatic.save(newType);
                        });

                EXISTING_PRODUCT_TYPE_ID = productType.getId();
            });

            txHelperStatic.flushAndClear();
        } finally {
            BankContextHolder.clear();
        }
    }

    /**
     * Clean up committed test data (created via committed helper or API) after each test.
     */
    @AfterEach
    void tearDown() {
        txHelper.doInTransaction(() -> {
            cleanupTestEntities(productRepository, featureLinkRepository, pricingLinkRepository);
        });

        // Clear the EntityManager cache
        productRepository.flush();
        entityManager.clear();
    }

    /**
     * Cleans up ONLY the entities created during the test run (Products and Links).
     * It DOES NOT delete the static ProductType, which must persist.
     */
    private static void cleanupTestEntities(ProductRepository productRepo, ProductFeatureLinkRepository featureLinkRepo, ProductPricingLinkRepository pricingLinkRepo) {
        // 1. Clear Links FIRST (crucial for FKs)
        featureLinkRepo.deleteAllInBatch();
        pricingLinkRepo.deleteAllInBatch();

        // 2. Clear Products (the primary test entity)
        productRepo.deleteAllInBatch();

        // NOTE: productTypeRepo.deleteAllInBatch() is intentionally REMOVED here.
    }

    // =================================================================
    // HELPER METHODS
    // =================================================================

    /**
     * Helper to create a product type entity.
     */
    private Long createProductType(String name) {
        ProductType productType = new ProductType();
        productType.setName(name);
        productType.setBankId(TEST_BANK_ID);
        return productTypeRepository.save(productType).getId();
    }

    /**
     * Main helper to create a Product via the API call.
     * Requires the calling test to have the 'catalog:product:create' permission.
     * NOTE: This commits data (via HTTP POST), requiring cleanup in @AfterEach.
     */
    private Long createProductViaApi(String status, String name, String bankId, Long productTypeId,
                                     LocalDate effectiveDate, LocalDate expirationDate) throws Exception {
        CreateProductRequestDto dto = new CreateProductRequestDto();
        dto.setName(name);
        dto.setBankId(bankId);
        dto.setEffectiveDate(effectiveDate);
        dto.setExpirationDate(expirationDate);
        dto.setStatus(status);
        dto.setProductTypeId(productTypeId);

        String json = mockMvc.perform(post(PRODUCT_API_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(json);
        return root.path("id").asLong();
    }

    /**
     * Simplified helper for common product creation scenarios (Active/Draft).
     */
    private Long createProductViaApi(String status) throws Exception {
        LocalDate effectiveDate = status.equals("ACTIVE")
                ? LocalDate.now().minusDays(1)
                : LocalDate.now().plusDays(1);

        return createProductViaApi(status, status + " Product", TEST_BANK_ID,
                EXISTING_PRODUCT_TYPE_ID, effectiveDate, null);
    }

    /**
     * Standard DTO for creation tests (no complex logic, just default values).
     */
    private CreateProductRequestDto getStandardCreateDto(String status) {
        CreateProductRequestDto dto = new CreateProductRequestDto();
        dto.setName("Standard Product Name");
        dto.setBankId("BC-STD");
        dto.setEffectiveDate(LocalDate.now().plusDays(1));
        dto.setStatus(status);
        dto.setProductTypeId(EXISTING_PRODUCT_TYPE_ID);
        return dto;
    }

    /**
     * Helper to create multiple products for search testing.
     */
    private void setupMultipleProductsForSearch() throws Exception {
        // --- PRODUCTS FOR CURRENT TENANT (TEST_BANK_ID) - EXPECTED TO BE VISIBLE (4 TOTAL) ---
        createProductViaApi("DRAFT", "Draft Product A", TEST_BANK_ID, EXISTING_PRODUCT_TYPE_ID, LocalDate.now().plusDays(1), null);
        createProductViaApi("ACTIVE", "Active Checking", TEST_BANK_ID, EXISTING_PRODUCT_TYPE_ID, LocalDate.now().minusDays(1), null);

        Long typeBId = createProductType("Type B");
        createProductViaApi("INACTIVE", "Inactive Savings", TEST_BANK_ID, typeBId, LocalDate.now().minusMonths(1), LocalDate.now().minusDays(10));
        createProductViaApi("ACTIVE", "Premium Card", TEST_BANK_ID, EXISTING_PRODUCT_TYPE_ID, LocalDate.now().minusDays(1), null);

        // --- PRODUCTS FOR FOREIGN TENANT (OTHER_BANK_ID) - EXPECTED TO BE HIDDEN (2 TOTAL) ---
        BankContextHolder.setBankId(OTHER_BANK_ID);
        try {
            // Create Product Type for the foreign bank
            Long foreignTypeId = createProductType("Foreign Type X");

            // Create products explicitly tagged with the foreign bank ID
            createProductViaApi("ACTIVE", "Foreign Product 1", OTHER_BANK_ID, foreignTypeId, LocalDate.now().minusDays(1), null);
            createProductViaApi("DRAFT", "Foreign Product 2", OTHER_BANK_ID, foreignTypeId, LocalDate.now().plusDays(1), null);
        } finally {
            // CRITICAL: Always clear the context after creation
            BankContextHolder.clear();
        }
    }


// =================================================================
// TESTS START HERE
// =================================================================


//   Security & Authentication Tests

    @Test
    void shouldReturn401WhenAccessingSecureEndpointWithoutToken() throws Exception {
        mockMvc.perform(get(PRODUCT_API_BASE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn200WhenAccessingSecureEndpointWithToken() throws Exception {
        Product product = new Product();
        product.setName("Test Security Product");
        product.setBankId(TEST_BANK_ID);
        product.setProductType(productTypeRepository.findById(EXISTING_PRODUCT_TYPE_ID).get());
        productRepository.save(product);

        // ACT
        mockMvc.perform(get(PRODUCT_API_BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap())
                .andExpect(jsonPath("$.content").isArray());
    }

//  Create (POST) & Read (GET) Tests

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void shouldReturn403WhenCreatingProductWithoutPermission() throws Exception {
        CreateProductRequestDto requestDto = getStandardCreateDto("DRAFT");
        mockMvc.perform(post(PRODUCT_API_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldCreateProductAndReturn201() throws Exception {
        CreateProductRequestDto requestDto = getStandardCreateDto("DRAFT");
        mockMvc.perform(post(PRODUCT_API_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Standard Product Name"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn404WhenCreatingProductWithNonExistentProductType() throws Exception {
        CreateProductRequestDto requestDto = getStandardCreateDto("DRAFT");
        requestDto.setProductTypeId(99999L);
        mockMvc.perform(post(PRODUCT_API_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn404WhenGettingNonExistentProduct() throws Exception {
        mockMvc.perform(get(PRODUCT_API_BASE + "/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void shouldReturn403WhenReadingProductsWithoutPermission() throws Exception {
        mockMvc.perform(get(PRODUCT_API_BASE))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn200AndPageableResponse() throws Exception {
        // This relies on the product being created via API (committed)
        // and only being cleaned up by @AfterEach.
        createProductViaApi("DRAFT");

        // ACT: Call GET /api/v1/products
        mockMvc.perform(get(PRODUCT_API_BASE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // This assertion should now pass, as only the one product exists.
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

// Metadata Update (PUT) Tests

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldUpdateMetadataWhenProductIsDraft() throws Exception {
        // ARRANGE: Create a DRAFT product (uses 'create' permission)
        Long productId = createProductViaApi("DRAFT");

        // ACT: Perform the metadata update (uses 'update' permission)
        UpdateProductRequestDto updateDto = new UpdateProductRequestDto();
        updateDto.setName("Updated Draft Name");
        updateDto.setBankId("BC-002");
        updateDto.setEffectiveDate(LocalDate.now().plusYears(1));
        updateDto.setExpirationDate(null);
        updateDto.setStatus("DRAFT"); // Status unchanged for metadata update

        mockMvc.perform(put(PRODUCT_API_BASE + "/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Draft Name"))
                .andExpect(jsonPath("$.bankId").value("BC-002"));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn400WhenUpdatingMetadataForActiveProduct() throws Exception {
        // ARRANGE: Create an ACTIVE product
        Long productId = createProductViaApi("ACTIVE");

        // ARRANGE: Setup an update DTO
        UpdateProductRequestDto updateDto = new UpdateProductRequestDto();
        updateDto.setName("Attempted Update");
        updateDto.setBankId("BC-001-A");
        updateDto.setEffectiveDate(LocalDate.now().plusDays(1));
        updateDto.setExpirationDate(null);
        updateDto.setStatus("ACTIVE");

        // ACT & ASSERT: Expect 400 Bad Request (Business rule: cannot modify metadata of an active product)
        mockMvc.perform(put(PRODUCT_API_BASE + "/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isBadRequest());
    }

    // Retained original separate 403 test logic for clarity
    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void shouldReturn403WhenUpdatingProductWithoutPermission() throws Exception {
        // The product created here is committed and will be cleaned up by @AfterEach.
        Long productId = txHelper.createProductInDb("Product to Update", EXISTING_PRODUCT_TYPE_ID);

        UpdateProductRequestDto updateDto = new UpdateProductRequestDto();
        updateDto.setName("Updated Draft Name");
        updateDto.setStatus("DRAFT");
        updateDto.setBankId("BANKID");

        mockMvc.perform(put(PRODUCT_API_BASE + "/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isForbidden());
    }

// Direct Action Tests (Activate/Deactivate/Extend)

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void shouldReturn403WhenActivatingProductWithoutPermission() throws Exception {
        // The product created here is committed and will be cleaned up by @AfterEach.
        Long productId = txHelper.createProductInDb("Product to Activate", EXISTING_PRODUCT_TYPE_ID);
        mockMvc.perform(post(PRODUCT_API_BASE + "/{id}/activate", productId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
        // ADMIN has 'activate'
    void shouldActivateDraftProductAndSetStatusToActive() throws Exception {
        // ARRANGE: Create a DRAFT product with future effective date
        Long productId = createProductViaApi("DRAFT");
        LocalDate activationDate = LocalDate.now().plusDays(10);

        // Simplified DTO creation for activation
        String activationDtoJson = objectMapper.writeValueAsString(new Object() {
            public LocalDate getEffectiveDate() {
                return activationDate;
            }
        });

        // ACT: Call POST /activate
        mockMvc.perform(post(PRODUCT_API_BASE + "/{id}/activate", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activationDtoJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.effectiveDate").value(activationDate.toString()));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
        // ADMIN has 'deactivate'
    void shouldDeactivateActiveProductAndSetStatusToInactive() throws Exception {
        // ARRANGE: Create an ACTIVE product
        Long productId = createProductViaApi("ACTIVE");

        // ACT: Call POST /deactivate
        mockMvc.perform(post(PRODUCT_API_BASE + "/{id}/deactivate", productId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.expirationDate").value(LocalDate.now().toString()));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
        // ADMIN has 'update'
    void shouldExtendProductExpirationDate() throws Exception {
        // ARRANGE: Create an ACTIVE product
        Long productId = createProductViaApi("ACTIVE");
        LocalDate newExpirationDate = LocalDate.now().plusYears(5);
        ProductExpirationDto expirationDto = new ProductExpirationDto();
        expirationDto.setExpirationDate(newExpirationDate);

        // ACT: Call PUT /expiration
        mockMvc.perform(put(PRODUCT_API_BASE + "/{id}/expiration", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expirationDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expirationDate").value(newExpirationDate.toString()));
    }

    // Versioning (Copy-and-Update) Tests

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldCreateNewVersionWhenActiveProductIsVersioned() throws Exception {
        BankContextHolder.setBankId(TEST_BANK_ID);
        try {
            // ARRANGE: Create a base ACTIVE product (V1)
            Product oldProduct = productRepository.findById(createProductViaApi("ACTIVE")).get();
            LocalDate newEffectiveDate = LocalDate.now().plusMonths(3);

            // ARRANGE: Link features and pricingComponent to the old product (These are auto-rolled back)
            FeatureComponent feature = new FeatureComponent("Test Feature", FeatureComponent.DataType.STRING);
            feature.setBankId(TEST_BANK_ID);
            feature = featureComponentRepository.save(feature);

            ProductFeatureLink featureLink = new ProductFeatureLink(oldProduct, feature, "Test Value");
            featureLink.setBankId(TEST_BANK_ID);
            featureLinkRepository.save(featureLink);

            PricingComponent pricingComponent = new PricingComponent("Test Pricing", PricingComponent.ComponentType.FEE);
            pricingComponent.setBankId(TEST_BANK_ID);
            pricingComponent = pricingComponentRepository.save(pricingComponent);

            ProductPricingLink pricingLink = new ProductPricingLink(oldProduct, pricingComponent, "Test Context", null, true);
            pricingLink.setBankId(TEST_BANK_ID);
            pricingLinkRepository.save(pricingLink);

            // Refresh the entity to ensure associations are loaded for the service call
            entityManager.flush();
            entityManager.refresh(oldProduct);

            // ARRANGE: Setup DTO
            CreateNewVersionRequestDto versionDto = new CreateNewVersionRequestDto("Gold Checking Account V2.0", newEffectiveDate);

            // ACT: Call POST /new-version
            String responseJson = mockMvc.perform(post(PRODUCT_API_BASE + "/{id}/new-version", oldProduct.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(versionDto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andReturn().getResponse().getContentAsString();

            Long newProductId = objectMapper.readValue(responseJson, ProductResponseDto.class).getId();

            // ASSERT 1: Verify the old product (V1) was archived
            mockMvc.perform(get(PRODUCT_API_BASE + "/{id}", oldProduct.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ARCHIVED"))
                    .andExpect(jsonPath("$.expirationDate").value(newEffectiveDate.minusDays(1).toString()));

            // ASSERT 2: Verify the new product (V2) has the cloned links
            mockMvc.perform(get(PRODUCT_API_BASE + "/{id}", newProductId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.features.length()").value(1))
                    .andExpect(jsonPath("$.features[0].featureName").value("Test Feature"))
                    .andExpect(jsonPath("$.pricing.length()").value(1));

        } finally {
            BankContextHolder.clear();
        }
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn400WhenVersioningDraftProduct() throws Exception {
        // ARRANGE: Create a DRAFT product
        Long productId = createProductViaApi("DRAFT");

        // ARRANGE: Setup CreateNewVersionRequestDto
        CreateNewVersionRequestDto versionDto = new CreateNewVersionRequestDto("Unauthorized Version", LocalDate.now().plusMonths(3));

        // ACT & ASSERT: Attempt versioning on DRAFT (Expect business logic failure)
        mockMvc.perform(post(PRODUCT_API_BASE + "/{id}/new-version", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(versionDto)))
                .andExpect(status().isBadRequest());
    }

// Search Integration Tests (JPA Specifications)

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturnAllProductsWhenNoFilterCriteriaAreProvided() throws Exception {
        setupMultipleProductsForSearch();

        mockMvc.perform(get(PRODUCT_API_BASE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content.length()").value(4));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldFilterProductsByStatusAndName() throws Exception {
        setupMultipleProductsForSearch();

        // ACT & ASSERT: Search for ACTIVE products where name contains 'Checking'
        mockMvc.perform(get(PRODUCT_API_BASE)
                        .param("status", "ACTIVE")
                        .param("name", "check")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Active Checking"));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldFilterProductsByBankIdAndProductType() throws Exception {
        // ARRANGE: Set up products with different types and banks
        Long typeCId = createProductType("Type C");
        createProductViaApi("ACTIVE", "Exclusive Product", TEST_BANK_ID, typeCId, LocalDate.now().minusDays(1), null);
        createProductViaApi("DRAFT", "Other Bank Product", "BANKD", EXISTING_PRODUCT_TYPE_ID, LocalDate.now().plusDays(1), null);

        // ACT & ASSERT: Search for products in BANKC with type ID = typeCId
        mockMvc.perform(get(PRODUCT_API_BASE)
                        .param("bankId", TEST_BANK_ID)
                        .param("productTypeId", String.valueOf(typeCId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Exclusive Product"));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturnZeroProductsWhenNoCriteriaMatch() throws Exception {
        setupMultipleProductsForSearch();

        // ACT & ASSERT: Search using a combination that won't match any product
        mockMvc.perform(get(PRODUCT_API_BASE)
                        .param("status", "ARCHIVED")
                        .param("bankId", "NONEXISTENT")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldFilterProductsByEffectiveDateRange() throws Exception {
        // ARRANGE: Set up three products with staggered dates.
        createProductViaApi("ACTIVE", "Current Product", TEST_BANK_ID, EXISTING_PRODUCT_TYPE_ID, LocalDate.now().minusDays(1), null); // T-1
        createProductViaApi("DRAFT", "Future Product", TEST_BANK_ID, EXISTING_PRODUCT_TYPE_ID, LocalDate.now().plusDays(1), null); // T+1
        createProductViaApi("ARCHIVED", "Old Product", TEST_BANK_ID, EXISTING_PRODUCT_TYPE_ID, LocalDate.now().minusMonths(1), LocalDate.now().minusDays(10)); // T-30

        // ACT & ASSERT 1: effectiveDateFrom >= tomorrow (Future products)
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        mockMvc.perform(get(PRODUCT_API_BASE)
                        .param("effectiveDateFrom", tomorrow.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Future Product"));

        // ACT & ASSERT 2: effectiveDateTo <= today (Current and Old products)
        LocalDate today = LocalDate.now();
        mockMvc.perform(get(PRODUCT_API_BASE)
                        .param("effectiveDateTo", today.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)); // ASSERT on 2 (Current + Old)
    }
}