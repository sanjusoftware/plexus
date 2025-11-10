package com.bankengine.catalog;

import com.bankengine.catalog.dto.ProductFeatureDto;
import com.bankengine.catalog.dto.ProductFeatureSyncDto;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ProductFeatureSyncIntegrationTest {

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
    private ProductFeatureLinkRepository linkRepository;

    @Value("${security.jwt.secret-key}")
    private String jwtSecretKey;

    @Value("${security.jwt.issuer-uri}")
    private String jwtIssuerUri;

    private JwtTestUtil jwtTestUtil;

    // Entities used for setup
    private Product product;
    private FeatureComponent componentA; // Integer type, value "10"
    private FeatureComponent componentB; // Boolean type, value "true"
    private FeatureComponent componentC; // Decimal type, value "5.50"

    // Helper method to create DTOs
    private ProductFeatureDto createFeatureDto(FeatureComponent component, String value) {
        ProductFeatureDto dto = new ProductFeatureDto();
        dto.setProductId(product.getId());
        dto.setFeatureComponentId(component.getId());
        dto.setFeatureValue(value);
        return dto;
    }

    @BeforeEach
    void setup() {
        jwtTestUtil = new JwtTestUtil(jwtSecretKey, jwtIssuerUri);
        // 1. Setup ProductType
        ProductType type = new ProductType();
        type.setName("Checking Account");
        productTypeRepository.save(type);

        // 2. Setup Product
        product = new Product();
        product.setName("Sync Test Product");
        product.setProductType(type);
        productRepository.save(product);

        // 3. Setup Feature Components
        componentA = new FeatureComponent();
        componentA.setName("Monthly Limit");
        componentA.setDataType(FeatureComponent.DataType.INTEGER);
        featureComponentRepository.save(componentA);

        componentB = new FeatureComponent();
        componentB.setName("Free ATM Access");
        componentB.setDataType(FeatureComponent.DataType.BOOLEAN);
        featureComponentRepository.save(componentB);

        componentC = new FeatureComponent();
        componentC.setName("Cashback Rate");
        componentC.setDataType(FeatureComponent.DataType.DECIMAL);
        featureComponentRepository.save(componentC);
    }

    // =================================================================
    // 1. INITIAL CREATE (No existing links)
    // =================================================================

    @Test
    void shouldCreateNewFeaturesWhenNoLinksExist() throws Exception {
        String token = jwtTestUtil.createToken("test-user", List.of("catalog:product:update"));
        // ARRANGE: Target state includes A and B
        ProductFeatureSyncDto syncDto = new ProductFeatureSyncDto();
        syncDto.setFeatures(List.of(
                createFeatureDto(componentA, "20"), // New link A
                createFeatureDto(componentB, "false") // New link B
        ));

        // ACT: Synchronize
        mockMvc.perform(put("/api/v1/products/{id}/features", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()", is(2)));

        // VERIFY: Check DB count
        assertThat(linkRepository.findByProductId(product.getId())).hasSize(2);
    }

    // =================================================================
    // 2. CREATE, UPDATE, DELETE (Full Sync)
    // =================================================================

    @Test
    void shouldPerformFullSync_CreateUpdateAndDelete() throws Exception {
        String token = jwtTestUtil.createToken("test-user", List.of("catalog:product:update"));
        // ARRANGE: Setup initial state (Only A and C linked)
        linkRepository.save(createInitialLink(product, componentA, "10")); // Initial A
        linkRepository.save(createInitialLink(product, componentC, "3.0")); // Initial C

        // ARRANGE: Target state (B is created, A is updated, C is deleted)
        ProductFeatureSyncDto syncDto = new ProductFeatureSyncDto();
        syncDto.setFeatures(List.of(
                createFeatureDto(componentA, "50"), // A is updated (10 -> 50)
                createFeatureDto(componentB, "true") // B is created
        ));
        // Note: Component C (3.0) is missing from the list, so it should be deleted.

        // ACT: Synchronize
        mockMvc.perform(put("/api/v1/products/{id}/features", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()", is(2)));

        // VERIFY 1: Check DB final state (Only A and B exist)
        List<ProductFeatureLink> finalLinks = linkRepository.findByProductId(product.getId());
        assertThat(finalLinks.stream().map(l -> l.getFeatureComponent().getId()).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(componentA.getId(), componentB.getId());

        // VERIFY 2: Check update (A's value should be 50)
        ProductFeatureLink linkA = finalLinks.stream()
                .filter(l -> l.getFeatureComponent().getId().equals(componentA.getId()))
                .findFirst().orElseThrow();
        assertThat(linkA.getFeatureValue()).isEqualTo("50");

        // VERIFY 3: Check deletion (C link should be gone)
        assertThat(linkRepository.findByFeatureComponentId(componentC.getId())).isEmpty();
    }

    // =================================================================
    // 3. ERROR HANDLING (Validation)
    // =================================================================

    @Test
    void shouldReturn400OnSyncWithInvalidFeatureValueType() throws Exception {
        String token = jwtTestUtil.createToken("test-user", List.of("catalog:product:update"));
        // ARRANGE: Attempt to set a STRING value on a BOOLEAN feature (B)
        ProductFeatureSyncDto syncDto = new ProductFeatureSyncDto();
        syncDto.setFeatures(List.of(
                createFeatureDto(componentB, "Not a boolean value")
        ));

        // ACT & ASSERT: Expect 400 Bad Request due to validation failure
        mockMvc.perform(put("/api/v1/products/{id}/features", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("must be 'true' or 'false' for BOOLEAN")));
    }

    @Test
    void shouldReturn404OnSyncWithNonExistentProduct() throws Exception {
        String token = jwtTestUtil.createToken("test-user", List.of("catalog:product:update"));
        ProductFeatureSyncDto syncDto = new ProductFeatureSyncDto();
        syncDto.setFeatures(List.of(createFeatureDto(componentA, "1")));

        mockMvc.perform(put("/api/v1/products/99999/features")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Product not found")));
    }

    // =================================================================
    // Helper to create an initial link entity
    // =================================================================
    private ProductFeatureLink createInitialLink(Product p, FeatureComponent fc, String value) {
        ProductFeatureLink link = new ProductFeatureLink();
        link.setProduct(p);
        link.setFeatureComponent(fc);
        link.setFeatureValue(value);
        return link;
    }
}