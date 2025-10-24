package com.bankengine.catalog;

import com.bankengine.catalog.dto.CreateProductRequestDto;
import com.bankengine.catalog.dto.ProductResponseDto;
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
    private CreateProductRequestDto getValidCreateProductRequestDto() {
        CreateProductRequestDto dto = new CreateProductRequestDto();
        dto.setName("Gold Checking Account");
        dto.setBankId("BC-001");
        dto.setEffectiveDate(LocalDate.now());
        dto.setStatus("ACTIVE");
        dto.setProductTypeId(EXISTING_PRODUCT_TYPE_ID);
        return dto;
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
        CreateProductRequestDto requestDto = getValidCreateProductRequestDto();

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated()) // Expect 201 Created
                .andExpect(jsonPath("$.name").value("Gold Checking Account"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    @WithMockUser
    void shouldReturn404WhenCreatingProductWithNonExistentProductType() throws Exception {
        CreateProductRequestDto requestDto = getValidCreateProductRequestDto();
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

    @Test
    @WithMockUser
    void shouldArchiveProductAndSetStatusToInactive() throws Exception {
        // ARRANGE: Create a product first
        String productJson = mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(getValidCreateProductRequestDto())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        ProductResponseDto createdProduct = objectMapper.readValue(productJson, ProductResponseDto.class);
        Long productId = createdProduct.getId();

        // ACT: Call DELETE /api/v1/products/{id}
        mockMvc.perform(delete("/api/v1/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // ASSERT: Check that the status is correctly changed to the terminal state
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                // ASSERT: Check that the expirationDate is set (to today's date, which is non-null)
                .andExpect(jsonPath("$.expirationDate").exists())
                // ASSERT: Check that the updatedAt timestamp is present (due to JPA Auditing)
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @WithMockUser
    void shouldReturn404WhenArchivingNonExistentProduct() throws Exception {
        Long nonExistentId = 99999L; // ID guaranteed not to exist

        // ACT: Attempt to archive a non-existent product
        mockMvc.perform(delete("/api/v1/products/{id}", nonExistentId)
                        .contentType(MediaType.APPLICATION_JSON))
                // ASSERT: Expect 404 Not Found due to getProductEntityById() in the service
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}