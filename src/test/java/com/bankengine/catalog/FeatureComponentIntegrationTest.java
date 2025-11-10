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
import com.bankengine.utils.JwtTestUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Value("${security.jwt.secret-key}")
    private String jwtSecretKey;

    @Value("${security.jwt.issuer-uri}")
    private String jwtIssuerUri;

    private JwtTestUtil jwtTestUtil;

    // Shared data entities
    private Product sharedProduct;

    @BeforeEach
    void setup() {
        jwtTestUtil = new JwtTestUtil(jwtSecretKey, jwtIssuerUri);
        setupDependencyProduct();
    }

    void setupDependencyProduct() {
        // Create a minimal ProductType dependency first
        ProductType sharedProductType = new ProductType();
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
    void shouldReturn403WhenCreatingFeatureWithoutPermission() throws Exception {
        String token = jwtTestUtil.createToken("test-user", List.of("some:other:permission"));
        mockMvc.perform(post("/api/v1/features")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(getCreateDto("ForbiddenFeature"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldCreateFeatureAndReturn201() throws Exception {
        String token = jwtTestUtil.createToken("test-user", List.of("catalog:feature:create"));
        mockMvc.perform(post("/api/v1/features")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(getCreateDto("PremiumSupport"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("PremiumSupport")))
                .andExpect(jsonPath("$.dataType", is("STRING")))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void shouldReturn400OnCreateWithInvalidDataType() throws Exception {
        String token = jwtTestUtil.createToken("test-user", List.of("catalog:feature:create"));
        CreateFeatureComponentRequestDto dto = getCreateDto("BadTypeFeature");
        dto.setDataType("XYZ"); // Invalid data type

        mockMvc.perform(post("/api/v1/features")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest()) // Handled by IllegalArgumentException
                .andExpect(jsonPath("$.message", is("Invalid data type provided: XYZ")));
    }

    // =================================================================
    // 2. RETRIEVE (GET) TESTS
    // =================================================================

    @Test
    void shouldReturn403WhenReadingFeatureWithoutPermission() throws Exception {
        String token = jwtTestUtil.createToken("test-user", List.of("some:other:permission"));
        FeatureComponent savedComponent = createFeatureComponentInDb("ForbiddenFeature");
        mockMvc.perform(get("/api/v1/features/{id}", savedComponent.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn200AndFeatureById() throws Exception {
        String token = jwtTestUtil.createToken("test-user", List.of("catalog:feature:read"));
        FeatureComponent savedComponent = createFeatureComponentInDb("ATMWithdrawals");

        mockMvc.perform(get("/api/v1/features/{id}", savedComponent.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("ATMWithdrawals")))
                .andExpect(jsonPath("$.id", is(savedComponent.getId().intValue())));
    }

    @Test
    void shouldReturn404WhenGettingNonExistentFeature() throws Exception {
        String token = jwtTestUtil.createToken("test-user", List.of("catalog:feature:read"));
        mockMvc.perform(get("/api/v1/features/99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound()) // Handled by NotFoundException
                .andExpect(jsonPath("$.status", is(404)));
    }

    // =================================================================
    // 3. UPDATE (PUT) TESTS
    // =================================================================

    @Test
    void shouldReturn403WhenUpdatingFeatureWithoutPermission() throws Exception {
        String token = jwtTestUtil.createToken("test-user", List.of("some:other:permission"));
        FeatureComponent savedComponent = createFeatureComponentInDb("ForbiddenFeature");
        UpdateFeatureComponentRequestDto updateDto = new UpdateFeatureComponentRequestDto();
        updateDto.setName("NewName");
        updateDto.setDataType("BOOLEAN");

        mockMvc.perform(put("/api/v1/features/{id}", savedComponent.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldUpdateFeatureAndReturn200() throws Exception {
        String token = jwtTestUtil.createToken("test-user", List.of("catalog:feature:update"));
        FeatureComponent savedComponent = createFeatureComponentInDb("OldName");
        UpdateFeatureComponentRequestDto updateDto = new UpdateFeatureComponentRequestDto();
        updateDto.setName("NewName");
        updateDto.setDataType("BOOLEAN");

        mockMvc.perform(put("/api/v1/features/{id}", savedComponent.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("NewName")))
                .andExpect(jsonPath("$.dataType", is("BOOLEAN")))
                .andExpect(jsonPath("$.updatedAt").exists()); // Check Auditing
    }

    @Test
    void shouldReturn404OnUpdateNonExistentFeature() throws Exception {
        String token = jwtTestUtil.createToken("test-user", List.of("catalog:feature:update"));
        UpdateFeatureComponentRequestDto updateDto = new UpdateFeatureComponentRequestDto();
        updateDto.setName("Test");
        updateDto.setDataType("STRING");

        mockMvc.perform(put("/api/v1/features/99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isNotFound());
    }

    // =================================================================
    // 4. DELETE (DELETE) TESTS - CRITICAL DEPENDENCY CHECKS
    // =================================================================

    @Test
    void shouldReturn403WhenDeletingFeatureWithoutPermission() throws Exception {
        String token = jwtTestUtil.createToken("test-user", List.of("some:other:permission"));
        FeatureComponent savedComponent = createFeatureComponentInDb("ForbiddenFeature");
        mockMvc.perform(delete("/api/v1/features/{id}", savedComponent.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldDeleteFeatureAndReturn204() throws Exception {
        String token = jwtTestUtil.createToken("test-user", List.of("catalog:feature:delete", "catalog:feature:read"));
        FeatureComponent savedComponent = createFeatureComponentInDb("DeletableFeature");
        Long idToDelete = savedComponent.getId();

        mockMvc.perform(delete("/api/v1/features/{id}", idToDelete)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent()); // Expect 204 No Content

        // Verify it was actually deleted from the repository
        mockMvc.perform(get("/api/v1/features/{id}", idToDelete)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn409WhenDeletingLinkedFeature() throws Exception {
        String token = jwtTestUtil.createToken("test-user", List.of("catalog:feature:delete"));
        // Create a feature component
        FeatureComponent linkedComponent = createFeatureComponentInDb("LinkedFeature");

        // Create the ProductFeatureLink using the Product
        ProductFeatureLink link = new ProductFeatureLink();
        link.setFeatureComponent(linkedComponent);
        link.setProduct(sharedProduct);
        link.setFeatureValue("Default Value Set");

        linkRepository.save(link);

        // Attempt to delete the linked component
        mockMvc.perform(delete("/api/v1/features/{id}", linkedComponent.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cannot delete Feature Component ID")));
    }
}