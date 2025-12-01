package com.bankengine.catalog;

import com.bankengine.auth.config.test.WithMockRole;
import com.bankengine.auth.security.BankContextHolder;
import com.bankengine.catalog.dto.CreateProductTypeRequestDto;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.pricing.TestTransactionHelper;
import com.bankengine.pricing.repository.ProductPricingLinkRepository;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductTypeIntegrationTest extends AbstractIntegrationTest {

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

    @Autowired
    private TestTransactionHelper txHelper;

    // --- Role Constants ---
    public static final String ROLE_PREFIX = "PTIT_";
    private static final String CREATOR_ROLE = ROLE_PREFIX + "PRODUCT_TYPE_CREATOR";
    private static final String READER_ROLE = ROLE_PREFIX + "PRODUCT_TYPE_READER";
    private static final String UNAUTHORIZED_ROLE = ROLE_PREFIX + "UNAUTHORIZED";


    // =================================================================
    // SETUP AND TEARDOWN (Committed Data Lifecycle)
    // =================================================================

    /**
     * Set up committed roles required for security context loading.
     */
    @BeforeAll
    static void setupCommittedRoles(@Autowired TestTransactionHelper txHelperStatic) {
        // CREATOR needs read/create permission
        Set<String> creatorAuths = Set.of("catalog:product-type:create", "catalog:product-type:read");
        // READER only needs read permission
        Set<String> readerAuths = Set.of("catalog:product-type:read");
        // UNAUTHORIZED has no relevant permission
        Set<String> unauthorizedAuths = Set.of("some:other:permission");

        txHelperStatic.createRoleInDb(CREATOR_ROLE, creatorAuths);
        txHelperStatic.createRoleInDb(READER_ROLE, readerAuths);
        txHelperStatic.createRoleInDb(UNAUTHORIZED_ROLE, unauthorizedAuths);

        txHelperStatic.flushAndClear();
    }

    /**
     * Cleans up all committed data, including entities created by API/helper calls.
     * Note: This is now essential since @Transactional is removed.
     */
    @AfterEach
    void cleanUp() {
        txHelper.doInTransaction(() -> {
            productFeatureLinkRepository.deleteAllInBatch();
            productPricingLinkRepository.deleteAllInBatch();
            productRepository.deleteAllInBatch();
            productTypeRepository.deleteAllInBatch();
        });
        txHelper.flushAndClear();
    }

    private ProductType createAndSaveProductType(String name) {
        final String currentBankId = BankContextHolder.getBankId();
        return txHelper.doInTransaction(() -> {
            ProductType type = new ProductType();
            type.setName(name);
            type.setBankId(currentBankId);
            return productTypeRepository.save(type);
        });
    }

// --------------------------------------------------------------------------------
//                                 GET /api/v1/product-types
// --------------------------------------------------------------------------------

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn200AndAllProductTypes() throws Exception {
        // Arrange: Data is committed via the helper method
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
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn200AndEmptyListWhenNoProductTypesExist() throws Exception {
        // Arrange: Database is empty due to @AfterEach cleanup

        // Act & Assert
        mockMvc.perform(get(API_URL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE}) // User lacks the 'read' permission
    void shouldReturn403WhenReadingWithoutPermission() throws Exception {
        mockMvc.perform(get(API_URL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

// --------------------------------------------------------------------------------
//                                 POST /api/v1/product-types
// --------------------------------------------------------------------------------

    @Test
    @WithMockRole(roles = {CREATOR_ROLE})
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
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void shouldReturn403WhenCreatingWithoutPermission() throws Exception {
        CreateProductTypeRequestDto requestDto = new CreateProductTypeRequestDto();
        requestDto.setName("Forbidden Type");

        mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = {CREATOR_ROLE})
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
    @WithMockRole(roles = {CREATOR_ROLE})
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
    @WithMockRole(roles = {CREATOR_ROLE})
    void shouldReturn409ConflictWhenCreatingDuplicateName() throws Exception {
        // Arrange: Create the type once successfully (Committed via helper)
        createAndSaveProductType("Checking Account");

        // Arrange: Prepare the same request DTO again
        CreateProductTypeRequestDto requestDto = new CreateProductTypeRequestDto();
        requestDto.setName("Checking Account");

        // Act & Assert: Attempt to create it a second time (API call commits)
        mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                // The expected status for a database conflict (Unique Constraint Violation)
                .andExpect(status().isConflict()) // HttpStatus.CONFLICT (409) is appropriate
                .andExpect(jsonPath("$.message").exists());
    }
}