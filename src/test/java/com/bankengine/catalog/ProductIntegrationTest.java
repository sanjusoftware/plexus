package com.bankengine.catalog;

import com.bankengine.catalog.dto.*;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.FeatureComponentRepository;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.ProductPricingLink;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.repository.ProductPricingLinkRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional // Ensures tests roll back database changes
public class ProductIntegrationTest {

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

    // Use Long wrapper for easier null handling if needed, though here it's always set.
    private Long EXISTING_PRODUCT_TYPE_ID;
    private final String PRODUCT_API_BASE = "/api/v1/products";

    /**
     * Set up necessary supporting data before each test runs.
     * ONLY creates the base ProductType required by most tests.
     */
    @BeforeEach
    void setUp() {
        // 1. Explicitly clear both tables first
        productRepository.deleteAll();
        productTypeRepository.deleteAll(); // Clears the previous ProductType

        // 1. Create a ProductType Entity
        ProductType productType = new ProductType();
        productType.setName("Base Checking Type");
        ProductType savedType = productTypeRepository.save(productType);

        // 2. Store the generated ID for use in DTOs
        EXISTING_PRODUCT_TYPE_ID = savedType.getId();
        productTypeRepository.flush(); // Ensure commit/visibility
    }

    // =================================================================
    // HELPER METHODS
    // =================================================================

    /**
     * Helper to create a product type entity using the repository and return its ID.
     */
    private Long createProductType(String name) {
        ProductType productType = new ProductType();
        productType.setName(name);
        ProductType savedType = productTypeRepository.save(productType);
        productTypeRepository.flush();
        return savedType.getId();
    }

    /**
     * Main helper to create a Product via the API call.
     */
    private Long createProduct(String status, String name, String bankId, Long productTypeId,
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

        // Use JsonNode for robust ID extraction
        JsonNode root = objectMapper.readTree(json);
        return root.path("id").asLong();
    }

    /**
     * Simplified helper for common product creation scenarios (Active, default name/bank).
     * Requires the calling test to have 'catalog:product:create' permission.
     */
    private Long createProduct(String status) throws Exception {
        LocalDate effectiveDate = status.equals("ACTIVE")
            ? LocalDate.now().minusDays(1) // Active products are usually effective now or in the past
            : LocalDate.now().plusDays(1);  // Drafts are usually future-dated

        return createProduct(status, status + " Product", "BC-001",
                             EXISTING_PRODUCT_TYPE_ID, effectiveDate, null);
    }

    /**
     * Helper for basic product creation used in the search tests.
     * Requires the calling test to have 'catalog:product:create' permission.
     */
    private Long createProduct(String status, String name, String bankId, Long productTypeId) throws Exception {
        LocalDate effectiveDate = LocalDate.now().minusDays(1); // Default to past for simpler testing
        return createProduct(status, name, bankId, productTypeId, effectiveDate, null);
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
     * Helper to create multiple products for search testing with clean counts.
     * Requires the calling test to have 'catalog:product:create' permission.
     */
    private void setupMultipleProductsForSearch() throws Exception {
        // 1. DRAFT Product
        createProduct("DRAFT", "Draft Product A", "BANKA", EXISTING_PRODUCT_TYPE_ID, LocalDate.now().plusDays(1), null);

        // 2. ACTIVE Product (matches search criteria)
        createProduct("ACTIVE", "Active Checking", "BANKA", EXISTING_PRODUCT_TYPE_ID, LocalDate.now().minusDays(1), null);

        // 3. INACTIVE Product (different bankId/type)
        Long typeBId = createProductType("Type B");
        createProduct("INACTIVE", "Inactive Savings", "BANKB", typeBId, LocalDate.now().minusMonths(1), LocalDate.now().minusDays(10));

        // 4. ACTIVE Product (name partial match)
        createProduct("ACTIVE", "Premium Card", "BANKA", EXISTING_PRODUCT_TYPE_ID, LocalDate.now().minusDays(1), null);
    }

    // =================================================================
    // SECURITY TESTS (Authentication/Filter Tests - kept original style where relevant)
    // =================================================================

    @Test
    void shouldReturn401WhenAccessingSecureEndpointWithoutToken() throws Exception {
        // Testing the filter chain/unauthenticated access
        mockMvc.perform(get(PRODUCT_API_BASE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "catalog:product:read") // Replaced JWT for simplicity
    void shouldReturn200WhenAccessingSecureEndpointWithToken() throws Exception {
        Product product = new Product();
        product.setName("Test Security Product");
        product.setProductType(productTypeRepository.findById(EXISTING_PRODUCT_TYPE_ID).get());
        productRepository.save(product);

        // ACT
        mockMvc.perform(get(PRODUCT_API_BASE)) // No header needed due to @WithMockUser
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap())
                .andExpect(jsonPath("$.content").isArray());
    }

    // =================================================================
    // PRODUCT CRUD & 404/400 TESTS (All use @WithMockUser)
    // =================================================================

    @Test
    @WithMockUser(authorities = "some:other:permission")
    void shouldReturn403WhenCreatingProductWithoutPermission() throws Exception {
        CreateProductRequestDto requestDto = getStandardCreateDto("DRAFT");
        mockMvc.perform(post(PRODUCT_API_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"catalog:product:create"})
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
    @WithMockUser(authorities = {"catalog:product:create"})
    void shouldReturn404WhenCreatingProductWithNonExistentProductType() throws Exception {
        CreateProductRequestDto requestDto = getStandardCreateDto("DRAFT");
        requestDto.setProductTypeId(99999L);
        mockMvc.perform(post(PRODUCT_API_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(authorities = "catalog:product:read")
    void shouldReturn404WhenGettingNonExistentProduct() throws Exception {
        mockMvc.perform(get(PRODUCT_API_BASE + "/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // =================================================================
    // GET ALL (Pagination/Search Base)
    // =================================================================

    @Test
    @WithMockUser(authorities = "some:other:permission")
    void shouldReturn403WhenReadingProductsWithoutPermission() throws Exception {
        mockMvc.perform(get(PRODUCT_API_BASE))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"catalog:product:create", "catalog:product:read"})
    void shouldReturn200AndPageableResponse() throws Exception {
        // ARRANGE: Create a product explicitly for this test (uses helper, requires 'create')
        createProduct("DRAFT");

        // ACT: Call GET /api/v1/products
        mockMvc.perform(get(PRODUCT_API_BASE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    // =================================================================
    // METADATA UPDATE (PUT /api/v1/products/{id})
    // =================================================================

    @Test
    @WithMockUser(authorities = {"catalog:product:create", "some:other:permission"}) // Needs create for helper
    void shouldReturn403WhenUpdatingProductWithoutPermission() throws Exception {
        Long productId = createProduct("DRAFT");
        UpdateProductRequestDto updateDto = new UpdateProductRequestDto();
        updateDto.setName("Updated Draft Name");
        updateDto.setBankId("BC-002");
        updateDto.setEffectiveDate(LocalDate.now().plusYears(1));
        updateDto.setExpirationDate(null);
        updateDto.setStatus("DRAFT");

        // ACT: Only grant 'some:other:permission' for the PUT call
        mockMvc.perform(put(PRODUCT_API_BASE + "/{id}", productId)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user("test-user").authorities(new SimpleGrantedAuthority("some:other:permission")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"catalog:product:create", "catalog:product:update"})
    void shouldUpdateMetadataWhenProductIsDraft() throws Exception {
        // ARRANGE: Create a DRAFT product (requires 'create' permission)
        Long productId = createProduct("DRAFT");

        // ACT: Perform the metadata update (requires 'update' permission)
        UpdateProductRequestDto updateDto = new UpdateProductRequestDto();
        updateDto.setName("Updated Draft Name");
        updateDto.setBankId("BC-002");
        updateDto.setEffectiveDate(LocalDate.now().plusYears(1));
        updateDto.setExpirationDate(null);
        updateDto.setStatus("DRAFT");

        mockMvc.perform(put(PRODUCT_API_BASE + "/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Draft Name"))
                .andExpect(jsonPath("$.bankId").value("BC-002"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @WithMockUser(authorities = {"catalog:product:create", "catalog:product:update"})
    void shouldReturn403WhenUpdatingMetadataForActiveProduct() throws Exception {
        // ARRANGE: Create an ACTIVE product (requires 'create' permission)
        Long productId = createProduct("ACTIVE");

        // ARRANGE: Setup an update DTO
        UpdateProductRequestDto updateDto = new UpdateProductRequestDto();
        updateDto.setName("Attempted Update");
        updateDto.setBankId("BC-001-A");
        updateDto.setEffectiveDate(LocalDate.now().plusDays(1));
        updateDto.setExpirationDate(null);
        updateDto.setStatus("ACTIVE");

        // ACT & ASSERT: Attempt to update metadata (requires 'update' permission, expects 400 business logic failure)
        mockMvc.perform(put(PRODUCT_API_BASE + "/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isBadRequest()); // Expect 400 Bad Request (Business logic failure)
    }

    // =================================================================
    // DIRECT ACTION TESTS (Activate/Deactivate/Extend)
    // =================================================================

    @Test
    @WithMockUser(authorities = {"catalog:product:create", "some:other:permission"}) // Needs create for helper
    void shouldReturn403WhenActivatingProductWithoutPermission() throws Exception {
        Long productId = createProduct("DRAFT");

        // ACT: Only grant 'some:other:permission' for the POST call
        mockMvc.perform(post(PRODUCT_API_BASE + "/{id}/activate", productId)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user("test-user").authorities(new SimpleGrantedAuthority("some:other:permission"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"catalog:product:create", "some:other:permission"}) // Needs create for helper
    void shouldReturn403WhenDeactivatingProductWithoutPermission() throws Exception {
        Long productId = createProduct("ACTIVE");

        // ACT: Only grant 'some:other:permission' for the POST call
        mockMvc.perform(post(PRODUCT_API_BASE + "/{id}/deactivate", productId)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user("test-user").authorities(new SimpleGrantedAuthority("some:other:permission"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"catalog:product:create", "catalog:product:activate"})
    void shouldActivateDraftProductAndSetStatusToActive() throws Exception {
        // ARRANGE: Create a DRAFT product
        Long productId = createProduct("DRAFT", "To Be Activated", "BC-001", EXISTING_PRODUCT_TYPE_ID, LocalDate.now().plusDays(5), null);

        // ARRANGE: Activation DTO
        LocalDate activationDate = LocalDate.now().plusDays(10);
        String activationDtoJson = objectMapper.writeValueAsString(new Object() {
            public LocalDate getEffectiveDate() { return activationDate; }
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
    @WithMockUser(authorities = {"catalog:product:create", "catalog:product:deactivate"})
    void shouldDeactivateActiveProductAndSetStatusToInactive() throws Exception {
        // ARRANGE: Create an ACTIVE product
        Long productId = createProduct("ACTIVE");

        // ACT: Call POST /deactivate
        mockMvc.perform(post(PRODUCT_API_BASE + "/{id}/deactivate", productId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.expirationDate").value(LocalDate.now().toString()));
    }

    @Test
    @WithMockUser(authorities = {"catalog:product:create", "catalog:product:update"})
    void shouldExtendProductExpirationDate() throws Exception {
        // ARRANGE: Create an ACTIVE product
        Long productId = createProduct("ACTIVE");
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

    // =================================================================
    // VERSIONING (COPY-AND-UPDATE) TESTS
    // =================================================================

    @Test
    @WithMockUser(authorities = {"catalog:product:create", "catalog:product:read", "catalog:product:update"})
    void shouldCreateNewVersionWhenActiveProductIsVersioned() throws Exception {
        // ARRANGE: Create a base ACTIVE product (V1)
        Product oldProduct = productRepository.findById(createProduct("ACTIVE")).get();
        LocalDate newEffectiveDate = LocalDate.now().plusMonths(3);

        // ARRANGE: Link some features and pricing to the old product
        FeatureComponent feature = new FeatureComponent();
        feature.setName("Test Feature");
        feature.setDataType(FeatureComponent.DataType.STRING);
        featureComponentRepository.save(feature);

        ProductFeatureLink featureLink = new ProductFeatureLink();
        featureLink.setProduct(oldProduct);
        featureLink.setFeatureComponent(feature);
        featureLink.setFeatureValue("Test Value");
        featureLinkRepository.save(featureLink);

        PricingComponent pricing = new PricingComponent();
        pricing.setName("Test Pricing");
        pricing.setType(PricingComponent.ComponentType.FEE);
        pricingComponentRepository.save(pricing);

        ProductPricingLink pricingLink = new ProductPricingLink();
        pricingLink.setProduct(oldProduct);
        pricingLink.setPricingComponent(pricing);
        pricingLink.setContext("Test Context");
        pricingLinkRepository.save(pricingLink);

        entityManager.flush();
        entityManager.clear();

        oldProduct = productRepository.findById(oldProduct.getId()).get();

        // ARRANGE: Setup CreateNewVersionRequestDto
        CreateNewVersionRequestDto versionDto = new CreateNewVersionRequestDto();
        versionDto.setNewName("Gold Checking Account V2.0");
        versionDto.setNewEffectiveDate(newEffectiveDate);

        // ACT: Call POST /new-version (requires 'create' permission)
        String responseJson = mockMvc.perform(post(PRODUCT_API_BASE + "/{id}/new-version", oldProduct.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(versionDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();

        Long newProductId = objectMapper.readValue(responseJson, ProductResponseDto.class).getId();

        // ASSERT 1: Verify the old product (V1) was archived (requires 'read' permission)
        mockMvc.perform(get(PRODUCT_API_BASE + "/{id}", oldProduct.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.expirationDate").value(newEffectiveDate.minusDays(1).toString()));

        // ASSERT 2: Verify the new product (V2) has the cloned links (requires 'read' permission)
        mockMvc.perform(get(PRODUCT_API_BASE + "/{id}", newProductId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()").value(1))
                .andExpect(jsonPath("$.features[0].featureName").value("Test Feature"))
                .andExpect(jsonPath("$.features[0].featureValue").value("Test Value"))
                .andExpect(jsonPath("$.pricing.length()").value(1))
                .andExpect(jsonPath("$.pricing[0].pricingComponentName").value("Test Pricing"))
                .andExpect(jsonPath("$.pricing[0].context").value("Test Context"));
    }

    @Test
    @WithMockUser(authorities = {"catalog:product:create"})
    void shouldReturn403WhenVersioningDraftProduct() throws Exception {
        // ARRANGE: Create a DRAFT product
        Long productId = createProduct("DRAFT");

        // ARRANGE: Setup CreateNewVersionRequestDto
        CreateNewVersionRequestDto versionDto = new CreateNewVersionRequestDto();
        versionDto.setNewName("Unauthorized Version");
        versionDto.setNewEffectiveDate(LocalDate.now().plusMonths(3));

        // ACT & ASSERT: Attempt versioning on DRAFT
        mockMvc.perform(post(PRODUCT_API_BASE + "/{id}/new-version", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(versionDto)))
                .andExpect(status().isBadRequest());
    }

    // =================================================================
    // SEARCH INTEGRATION TESTS (JPA SPECIFICATIONS)
    // =================================================================

    @Test
    @WithMockUser(authorities = {"catalog:product:create", "catalog:product:read"})
    void shouldReturnAllProductsWhenNoFilterCriteriaAreProvided() throws Exception {
        // ARRANGE: Set up 4 unique products across different statuses/banks
        setupMultipleProductsForSearch();

        // ACT & ASSERT: Send GET request with NO query parameters (requires 'read' permission)
        mockMvc.perform(get(PRODUCT_API_BASE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content.length()").value(4));
    }

    @Test
    @WithMockUser(authorities = {"catalog:product:create", "catalog:product:read"})
    void shouldFilterProductsByStatusAndName() throws Exception {
        // ARRANGE: Set up products
        setupMultipleProductsForSearch();

        // ACT & ASSERT: Search for ACTIVE products where name contains 'Checking'
        mockMvc.perform(get(PRODUCT_API_BASE)
                        .param("status", "ACTIVE")
                        .param("name", "check")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Active Checking"));
    }

    @Test
    @WithMockUser(authorities = {"catalog:product:create", "catalog:product:read"})
    void shouldFilterProductsByBankIdAndProductType() throws Exception {
        // ARRANGE: Set up products with different types and banks
        Long typeCId = createProductType("Type C");
        createProduct("ACTIVE", "Exclusive Product", "BANKC", typeCId);
        createProduct("DRAFT", "Other Bank Product", "BANKD", EXISTING_PRODUCT_TYPE_ID);

        // ACT & ASSERT: Search for products in BANKC with type ID = typeCId
        mockMvc.perform(get(PRODUCT_API_BASE)
                        .param("bankId", "BANKC")
                        .param("productTypeId", String.valueOf(typeCId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Exclusive Product"));
    }

    @Test
    @WithMockUser(authorities = {"catalog:product:create", "catalog:product:read"})
    void shouldReturnZeroProductsWhenNoCriteriaMatch() throws Exception {
        // ARRANGE: Set up products
        setupMultipleProductsForSearch();

        // ACT & ASSERT: Search using a combination that won't match any product
        mockMvc.perform(get(PRODUCT_API_BASE)
                        .param("status", "ARCHIVED")
                        .param("bankId", "NONEXISTENT")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @WithMockUser(authorities = {"catalog:product:create", "catalog:product:read"})
    void shouldFilterProductsByEffectiveDateRange() throws Exception {
        // ARRANGE: Set up three products with staggered dates.
        createProduct("ACTIVE", "Current Product", "BANKA", EXISTING_PRODUCT_TYPE_ID, LocalDate.now().minusDays(1), null);
        createProduct("DRAFT", "Future Product", "BANKA", EXISTING_PRODUCT_TYPE_ID, LocalDate.now().plusDays(1), null);
        createProduct("ARCHIVED", "Old Product", "BANKA", EXISTING_PRODUCT_TYPE_ID, LocalDate.now().minusMonths(1), LocalDate.now().minusDays(10));

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
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));
    }
}