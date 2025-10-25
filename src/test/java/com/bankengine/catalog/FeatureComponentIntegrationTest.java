package com.bankengine.catalog;

import com.bankengine.catalog.dto.CreateFeatureComponentRequestDto;
import com.bankengine.catalog.dto.UpdateFeatureComponentRequestDto;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.FeatureComponentRepository;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.is;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class FeatureComponentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FeatureComponentRepository featureComponentRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductTypeRepository productTypeRepository;

    @Autowired
    private ProductFeatureLinkRepository linkRepository;

    // Shared data entities
    private Product sharedProduct;
    private ProductType sharedProductType;

    @BeforeEach
    void setupDependencyProduct() {
        // Create a minimal ProductType dependency first
        sharedProductType = new ProductType();
        sharedProductType.setName("Test Type for Link");
        sharedProductType = productTypeRepository.save(sharedProductType);

        // Create a minimal Product entity to link to
        sharedProduct = new Product();
        sharedProduct.setName("Link Test Product");
        sharedProduct.setStatus("ACTIVE");
        sharedProduct.setProductType(sharedProductType);
        sharedProduct = productRepository.save(sharedProduct);
    }

    // Helper method to create a valid DTO for POST/PUT requests
    private CreateFeatureComponentRequestDto getCreateDto(String name) {
        CreateFeatureComponentRequestDto dto = new CreateFeatureComponentRequestDto();
        dto.setName(name);
        dto.setDataType("STRING");
        return dto;
    }

    // Helper method to create an entity directly in the DB
    private FeatureComponent createFeatureComponentInDb(String name) {
        FeatureComponent component = new FeatureComponent();
        component.setName(name);
        component.setDataType(FeatureComponent.DataType.STRING);
        return featureComponentRepository.save(component);
    }

    // =================================================================
    // 1. CREATE (POST) TESTS
    // =================================================================

    @Test
    @WithMockUser
    void shouldCreateFeatureAndReturn201() throws Exception {
        mockMvc.perform(post("/api/v1/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(getCreateDto("PremiumSupport"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("PremiumSupport")))
                .andExpect(jsonPath("$.dataType", is("STRING")))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    @WithMockUser
    void shouldReturn400OnCreateWithInvalidDataType() throws Exception {
        CreateFeatureComponentRequestDto dto = getCreateDto("BadTypeFeature");
        dto.setDataType("XYZ"); // Invalid data type

        mockMvc.perform(post("/api/v1/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest()) // Handled by IllegalArgumentException
                .andExpect(jsonPath("$.message", is("Invalid data type provided: XYZ")));
    }

    // =================================================================
    // 2. RETRIEVE (GET) TESTS
    // =================================================================

    @Test
    @WithMockUser
    void shouldReturn200AndFeatureById() throws Exception {
        FeatureComponent savedComponent = createFeatureComponentInDb("ATMWithdrawals");

        mockMvc.perform(get("/api/v1/features/{id}", savedComponent.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("ATMWithdrawals")))
                .andExpect(jsonPath("$.id", is(savedComponent.getId().intValue())));
    }

    @Test
    @WithMockUser
    void shouldReturn404WhenGettingNonExistentFeature() throws Exception {
        mockMvc.perform(get("/api/v1/features/99999"))
                .andExpect(status().isNotFound()) // Handled by NotFoundException
                .andExpect(jsonPath("$.status", is(404)));
    }

    // =================================================================
    // 3. UPDATE (PUT) TESTS
    // =================================================================

    @Test
    @WithMockUser
    void shouldUpdateFeatureAndReturn200() throws Exception {
        FeatureComponent savedComponent = createFeatureComponentInDb("OldName");
        UpdateFeatureComponentRequestDto updateDto = new UpdateFeatureComponentRequestDto();
        updateDto.setName("NewName");
        updateDto.setDataType("BOOLEAN");

        mockMvc.perform(put("/api/v1/features/{id}", savedComponent.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("NewName")))
                .andExpect(jsonPath("$.dataType", is("BOOLEAN")))
                .andExpect(jsonPath("$.updatedAt").exists()); // Check Auditing
    }

    @Test
    @WithMockUser
    void shouldReturn404OnUpdateNonExistentFeature() throws Exception {
        UpdateFeatureComponentRequestDto updateDto = new UpdateFeatureComponentRequestDto();
        updateDto.setName("Test");
        updateDto.setDataType("STRING");

        mockMvc.perform(put("/api/v1/features/99999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isNotFound());
    }

    // =================================================================
    // 4. DELETE (DELETE) TESTS - CRITICAL DEPENDENCY CHECKS
    // =================================================================

    @Test
    @WithMockUser
    void shouldDeleteFeatureAndReturn204() throws Exception {
        FeatureComponent savedComponent = createFeatureComponentInDb("DeletableFeature");
        Long idToDelete = savedComponent.getId();

        mockMvc.perform(delete("/api/v1/features/{id}", idToDelete))
                .andExpect(status().isNoContent()); // Expect 204 No Content

        // Verify it was actually deleted from the repository
        mockMvc.perform(get("/api/v1/features/{id}", idToDelete))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void shouldReturn409WhenDeletingLinkedFeature() throws Exception {
        // Create a feature component
        FeatureComponent linkedComponent = createFeatureComponentInDb("LinkedFeature");

        // Create the ProductFeatureLink using the Product
        ProductFeatureLink link = new ProductFeatureLink();
        link.setFeatureComponent(linkedComponent);
        link.setProduct(sharedProduct);
        link.setFeatureValue("Default Value Set");

        linkRepository.save(link);

        // Attempt to delete the linked component
        mockMvc.perform(delete("/api/v1/features/{id}", linkedComponent.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cannot delete Feature Component ID")));
    }
}