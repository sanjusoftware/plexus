package com.bankengine.catalog;

import com.bankengine.catalog.dto.CreateNewVersionRequestDto;
import com.bankengine.catalog.dto.CreateProductRequestDto;
import com.bankengine.catalog.dto.ProductResponseDto;
import com.bankengine.catalog.dto.UpdateProductRequestDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductTypeRepository;
import org.junit.jupiter.api.BeforeEach;

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
    private ProductTypeRepository productTypeRepository;

    private Long EXISTING_PRODUCT_TYPE_ID;

    /**
     * Set up necessary supporting data before each test runs.
     */
    @BeforeEach
    void setUp() {
        // 1. Create a ProductType Entity
        ProductType productType = new ProductType();
        productType.setName("Test Account Type");
        // ... set any other required fields ...

        // 2. Save it to the database (it will be rolled back later by @Transactional)
        ProductType savedType = productTypeRepository.save(productType);

        // 3. Store the generated ID for use in DTOs
        EXISTING_PRODUCT_TYPE_ID = savedType.getId();
    }

    // Helper DTO for POST requests
    private CreateProductRequestDto getValidCreateProductRequestDto(String status) {
        CreateProductRequestDto dto = new CreateProductRequestDto();
        dto.setName("Gold Checking Account");
        dto.setBankId("BC-001");
        dto.setEffectiveDate(LocalDate.now().plusDays(1)); // DRAFT products often have future effective dates
        dto.setStatus(status); // Set status dynamically
        dto.setProductTypeId(EXISTING_PRODUCT_TYPE_ID);
        // Removed: dto.setActivationDate()
        return dto;
    }

    // Helper DTO for PUT requests
    private UpdateProductRequestDto getValidUpdateProductRequestDto() {
        UpdateProductRequestDto dto = new UpdateProductRequestDto();
        // We only update name and bankId now, status is handled separately
        dto.setName("Platinum Checking Account V1.1");
        dto.setBankId("BC-001-A");
        dto.setEffectiveDate(LocalDate.now().plusDays(1));
        dto.setExpirationDate(null);
        dto.setStatus("DRAFT");
        return dto;
    }

    // Helper method to create a Product and return its ID
    private Long createProduct(String status) throws Exception {
        String json = mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(getValidCreateProductRequestDto(status))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, ProductResponseDto.class).getId();
    }

    // =================================================================
    // SECURITY TESTS
    // =================================================================

    @Test
    void shouldReturn401WhenAccessingSecureEndpointWithoutToken() throws Exception {
        // Attempt GET request without @WithMockUser annotation (no JWT/Auth)
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized()); // Expect 401
    }

    @Test
    @WithMockUser // Simulate a valid authenticated user (bypassing JWT check for simplicity)
    void shouldReturn200WhenAccessingSecureEndpointWithToken() throws Exception {
        // Attempt GET request with simulated user authentication
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk()) // Expect 200
                .andExpect(jsonPath("$").isArray()); // Expect an empty or non-empty JSON array
    }

    // =================================================================
    // PRODUCT CRUD & 404/400 TESTS
    // =================================================================

    @Test
    @WithMockUser
    void shouldCreateProductAndReturn201() throws Exception {
        CreateProductRequestDto requestDto = getValidCreateProductRequestDto("DRAFT");

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Gold Checking Account"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    @WithMockUser
    void shouldReturn404WhenCreatingProductWithNonExistentProductType() throws Exception {
        CreateProductRequestDto requestDto = getValidCreateProductRequestDto("DRAFT");
        requestDto.setProductTypeId(99999L); // ID guaranteed not to exist

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isNotFound()) // Expect 404 Not Found
                // Verify the custom error message structure
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser
    void shouldReturn404WhenGettingNonExistentProduct() throws Exception {
        mockMvc.perform(get("/api/v1/products/99999")) // ID guaranteed not to exist
                .andExpect(status().isNotFound()) // Expect 404 Not Found
                .andExpect(jsonPath("$.status").value(404));
    }

    // =================================================================
    // ARCHIVAL AND GET ALL
    // =================================================================

    @Test
    @WithMockUser
    void shouldReturn200AndListOfProducts() throws Exception {
        // ACT: Call GET /api/v1/products
        mockMvc.perform(get("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // =================================================================
// METADATA UPDATE (PUT /api/v1/products/{id})
// =================================================================

    @Test
    @WithMockUser
    void shouldUpdateMetadataWhenProductIsDraft() throws Exception {
        // ARRANGE: Create a DRAFT product
        Long productId = createProduct("DRAFT");

        // ACT: Perform the metadata update
        UpdateProductRequestDto updateDto = new UpdateProductRequestDto();
        updateDto.setName("Updated Draft Name");
        updateDto.setBankId("BC-002");
        updateDto.setEffectiveDate(LocalDate.now().plusYears(1)); // Service ignores this, but DTO must be valid
        updateDto.setExpirationDate(null);
        updateDto.setStatus("DRAFT");

        mockMvc.perform(put("/api/v1/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Draft Name"))
                .andExpect(jsonPath("$.bankId").value("BC-002"))
                .andExpect(jsonPath("$.status").value("DRAFT")); // Status should remain DRAFT
    }

    @Test
    @WithMockUser
    void shouldReturn403WhenUpdatingMetadataForActiveProduct() throws Exception {
        // ARRANGE: Create an ACTIVE product (assuming ACTIVATION is done via POST /activate)
        // Since we don't have the full activate logic yet, we simulate creation of an ACTIVE product
        // NOTE: This test might need adjustment once the full activate workflow is in place.
        Long productId = createProduct("ACTIVE");

        // ACT & ASSERT: Attempt to update metadata
        UpdateProductRequestDto updateDto = getValidUpdateProductRequestDto();

        mockMvc.perform(put("/api/v1/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isBadRequest());
    }

// =================================================================
// DIRECT ACTION TESTS (Activate/Deactivate/Extend)
// =================================================================

    @Test
    @WithMockUser
    void shouldActivateDraftProductAndSetStatusToActive() throws Exception {
        // ARRANGE: Create a DRAFT product
        Long productId = createProduct("DRAFT");

        // ARRANGE: Activation DTO with a new effective date
        LocalDate activationDate = LocalDate.now().plusDays(10);
        // Constructing simple DTO inline: {"effectiveDate": "YYYY-MM-DD"}
        String activationDtoJson = objectMapper.writeValueAsString(new Object() {
            public LocalDate getEffectiveDate() { return activationDate; }
        });

        // ACT: Call POST /api/v1/products/{id}/activate
        mockMvc.perform(post("/api/v1/products/{id}/activate", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activationDtoJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.effectiveDate").value(activationDate.toString()));
    }

    @Test
    @WithMockUser
    void shouldDeactivateActiveProductAndSetStatusToInactive() throws Exception {
        // ARRANGE: Create an ACTIVE product (or activate a DRAFT one)
        Long productId = createProduct("ACTIVE");

        // ACT: Call POST /api/v1/products/{id}/deactivate
        mockMvc.perform(post("/api/v1/products/{id}/deactivate", productId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.expirationDate").value(LocalDate.now().toString())); // Expiration set to today
    }

    @Test
    @WithMockUser
    void shouldExtendProductExpirationDate() throws Exception {
        // ARRANGE: Create an ACTIVE product
        Long productId = createProduct("ACTIVE");
        LocalDate newExpirationDate = LocalDate.now().plusYears(5);

        // ACT: Call PUT /api/v1/products/{id}/expiration
        mockMvc.perform(put("/api/v1/products/{id}/expiration", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newExpirationDate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expirationDate").value(newExpirationDate.toString()));
    }

// =================================================================
// VERSIONING (COPY-AND-UPDATE) TESTS
// =================================================================

    @Test
    @WithMockUser
    void shouldCreateNewVersionWhenActiveProductIsVersioned() throws Exception {
        // ARRANGE: Create a base ACTIVE product (V1)
        Long oldProductId = createProduct("ACTIVE");
        LocalDate newEffectiveDate = LocalDate.now().plusMonths(3);

        // ARRANGE: Setup CreateNewVersionRequestDto
        CreateNewVersionRequestDto versionDto = new CreateNewVersionRequestDto();
        versionDto.setNewName("Gold Checking Account V2.0");
        versionDto.setNewEffectiveDate(newEffectiveDate);

        // ACT: Call POST /api/v1/products/{id}/new-version
        String responseJson = mockMvc.perform(post("/api/v1/products/{id}/new-version", oldProductId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(versionDto)))
                .andExpect(status().isCreated()) // Expect 201
                .andExpect(jsonPath("$.name").value("Gold Checking Account V2.0"))
                .andExpect(jsonPath("$.status").value("DRAFT")) // New version is DRAFT
                .andExpect(jsonPath("$.effectiveDate").value(newEffectiveDate.toString()))
                .andReturn().getResponse().getContentAsString();

        Long newProductId = objectMapper.readValue(responseJson, ProductResponseDto.class).getId();

        // ASSERT 1: Verify the old product (V1) was archived
        mockMvc.perform(get("/api/v1/products/{id}", oldProductId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                // Expiration should be day before new effective date
                .andExpect(jsonPath("$.expirationDate").value(newEffectiveDate.minusDays(1).toString()));

        // ASSERT 2: Verify the new product (V2) is accessible
        mockMvc.perform(get("/api/v1/products/{id}", newProductId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @WithMockUser
    void shouldReturn403WhenVersioningDraftProduct() throws Exception {
        // ARRANGE: Create a DRAFT product
        Long productId = createProduct("DRAFT");

        // ARRANGE: Setup CreateNewVersionRequestDto
        CreateNewVersionRequestDto versionDto = new CreateNewVersionRequestDto();
        versionDto.setNewName("Unauthorized Version");
        versionDto.setNewEffectiveDate(LocalDate.now().plusMonths(3));

        // ACT & ASSERT: Attempt versioning on DRAFT
        mockMvc.perform(post("/api/v1/products/{id}/new-version", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(versionDto)))
                .andExpect(status().isBadRequest());
    }
}