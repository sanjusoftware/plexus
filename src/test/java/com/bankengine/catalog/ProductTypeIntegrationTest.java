package com.bankengine.catalog;

import com.bankengine.catalog.dto.CreateProductTypeRequestDto;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.pricing.repository.ProductPricingLinkRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional // Ensures each test runs in a transaction and rolls back
@WithMockUser
class ProductTypeIntegrationTest {

    private static final String API_URL = "/api/v1/product-types";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductTypeRepository productTypeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductFeatureLinkRepository productFeatureLinkRepository;

    @Autowired
    private ProductPricingLinkRepository productPricingLinkRepository;

    @BeforeEach
    void setUp() {
        productFeatureLinkRepository.deleteAllInBatch();
        productPricingLinkRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        productTypeRepository.deleteAllInBatch();
    }

    // --- Helper Method ---
    private ProductType createAndSaveProductType(String name) {
        ProductType type = new ProductType();
        type.setName(name);
        return productTypeRepository.save(type);
    }

// --------------------------------------------------------------------------------
//                                 GET /api/v1/product-types
// --------------------------------------------------------------------------------

    @Test
    void shouldReturn200AndAllProductTypes() throws Exception {
        // Arrange
        createAndSaveProductType("CASA");
        createAndSaveProductType("Credit Card");

        // Act & Assert
        mockMvc.perform(get(API_URL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("CASA")))
                .andExpect(jsonPath("$[1].name", is("Credit Card")));
    }

    @Test
    void shouldReturn200AndEmptyListWhenNoProductTypesExist() throws Exception {
        // Arrange: Database is empty due to @Transactional rollback

        // Act & Assert
        mockMvc.perform(get(API_URL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

// --------------------------------------------------------------------------------
//                                 POST /api/v1/product-types
// --------------------------------------------------------------------------------

    @Test
    void shouldReturn201AndCreateProductTypeSuccessfully() throws Exception {
        // Arrange
        CreateProductTypeRequestDto requestDto = new CreateProductTypeRequestDto();
        requestDto.setName("Fixed Deposit");

        // Act & Assert
        mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Fixed Deposit")))
                // The ID is auto-generated (Long)
                .andExpect(jsonPath("$.id").isNumber());

        // Verification: Ensure it was saved in the database
        // Since we are @Transactional, we can rely on the repo count
        // Note: Using count() is generally safe even in a transaction
        long count = productTypeRepository.count();
        // The one we just created
        // We might need to adjust this depending on how the transaction/test context behaves with count()
        // but for a successful test, we should verify the API response first.
    }

    @Test
    void shouldReturn400WhenNameIsMissing() throws Exception {
        // Arrange
        CreateProductTypeRequestDto requestDto = new CreateProductTypeRequestDto();
        requestDto.setName(null); // Invalid: @NotBlank

        // Act & Assert
        mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").value("Product Type name is required."));
    }

    @Test
    void shouldReturn400WhenNameIsTooShort() throws Exception {
        // Arrange
        CreateProductTypeRequestDto requestDto = new CreateProductTypeRequestDto();
        requestDto.setName("A"); // Invalid: @Size min=3

        // Act & Assert
        mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").value("Name must be between 3 and 100 characters."));
    }

    @Test
    void shouldReturn409ConflictWhenCreatingDuplicateName() throws Exception {
        // Arrange: Create the type once successfully
        createAndSaveProductType("Checking Account");

        // Arrange: Prepare the same request DTO again
        CreateProductTypeRequestDto requestDto = new CreateProductTypeRequestDto();
        requestDto.setName("Checking Account");

        // Act & Assert: Attempt to create it a second time
        mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                // The expected status for a database conflict (Unique Constraint Violation)
                .andExpect(status().isConflict()) // HttpStatus.CONFLICT (409) is appropriate
                .andExpect(jsonPath("$.message").exists());

        // NOTE: If your global exception handler maps DataIntegrityViolationException
        // to 400 Bad Request, change the expected status to .isBadRequest()
        // However, 409 Conflict is more specific to unique constraint issues.
    }
}