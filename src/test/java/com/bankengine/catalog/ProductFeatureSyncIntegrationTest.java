package com.bankengine.catalog;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.dto.ProductFeature;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.FeatureComponentRepository;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.pricing.TestTransactionHelper;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.bankengine.test.config.WithMockRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockRole(roles = {ProductFeatureSyncIntegrationTest.ADMIN_ROLE})
public class ProductFeatureSyncIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductTypeRepository productTypeRepository;
    @Autowired private FeatureComponentRepository featureComponentRepository;
    @Autowired private ProductFeatureLinkRepository linkRepository;
    @Autowired private TestTransactionHelper txHelper;

    public static final String ROLE_PREFIX = "PFST_";
    public static final String ADMIN_ROLE = ROLE_PREFIX + "CATALOG_ADMIN";
    public static final String READER_ROLE = ROLE_PREFIX + "CATALOG_READER";

    private static Product product;
    private static FeatureComponent componentA;
    private static FeatureComponent componentB;
    private static FeatureComponent componentC;

    @BeforeAll
    static void setupCommittedData(@Autowired TestTransactionHelper txHelperStatic,
                                   @Autowired ProductRepository productRepoStatic,
                                   @Autowired ProductTypeRepository productTypeRepoStatic,
                                   @Autowired FeatureComponentRepository featureComponentRepoStatic) {

        // 1. Seed Roles
        seedBaseRoles(txHelperStatic, Map.of(
            ADMIN_ROLE, Set.of("catalog:product:update", "catalog:feature:create", "catalog:feature:update", "catalog:feature:read"),
            READER_ROLE, Set.of("catalog:product:read")
        ));

        // 2. Setup Shared Entities
        try {
            TenantContextHolder.setBankId(TEST_BANK_ID);

            txHelperStatic.doInTransaction(() -> {
                BiFunction<String, FeatureComponent.DataType, FeatureComponent> findOrCreateFeatureComponent = (name, dataType) ->
                     featureComponentRepoStatic.findByName(name)
                        .orElseGet(() -> {
                            FeatureComponent component = new FeatureComponent();
                            component.setName(name);
                            component.setDataType(dataType);
                            return featureComponentRepoStatic.save(component);
                        });

                ProductType type = productTypeRepoStatic.findByName("Checking Account")
                        .orElseGet(() -> {
                            ProductType newType = new ProductType();
                            newType.setName("Checking Account");
                            return productTypeRepoStatic.save(newType);
                        });

                product = productRepoStatic.findByName("Sync Test Product")
                        .orElseGet(() -> {
                            Product p = new Product();
                            p.setName("Sync Test Product");
                            p.setProductType(type);
                            p.setCategory("RETAIL");
                            return productRepoStatic.save(p);
                        });

                componentA = findOrCreateFeatureComponent.apply("Monthly Limit", FeatureComponent.DataType.INTEGER);
                componentB = findOrCreateFeatureComponent.apply("Free ATM Access", FeatureComponent.DataType.BOOLEAN);
                componentC = findOrCreateFeatureComponent.apply("Cashback Rate", FeatureComponent.DataType.DECIMAL);
            });
            txHelperStatic.flushAndClear();
        } finally {
            TenantContextHolder.clear();
        }
    }

    @BeforeEach
    void setup() {
        txHelper.doInTransaction(() -> {
            linkRepository.deleteAllInBatch(linkRepository.findByProductId(product.getId()));
        });
        entityManager.clear();
    }

    private ProductFeature createFeatureDto(FeatureComponent component, String value) {
        ProductFeature dto = new ProductFeature();
        dto.setFeatureComponentId(component.getId());
        dto.setFeatureValue(value);
        return dto;
    }

    private ProductFeatureLink createInitialLink(Product p, FeatureComponent fc, String value) {
        ProductFeatureLink link = new ProductFeatureLink();
        link.setProduct(p);
        link.setFeatureComponent(fc);
        link.setFeatureValue(value);
        return link;
    }

    // --- TESTS ---

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn403WhenSyncingFeaturesWithoutPermission() throws Exception {
        List<ProductFeature> features = List.of(createFeatureDto(componentA, "20"));

        mockMvc.perform(put("/api/v1/products/{id}/features", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(features)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldCreateNewFeaturesWhenNoLinksExist() throws Exception {
        List<ProductFeature> features = List.of(
                createFeatureDto(componentA, "20"),
                createFeatureDto(componentB, "false")
        );

        mockMvc.perform(put("/api/v1/products/{id}/features", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(features)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()", is(2)));

        txHelper.doInTransaction(() -> {
            assertThat(linkRepository.findByProductId(product.getId())).hasSize(2);
        });
    }

    @Test
    void shouldPerformFullSync_CreateUpdateAndDelete() throws Exception {
        txHelper.doInTransaction(() -> {
            linkRepository.save(createInitialLink(product, componentA, "10"));
            linkRepository.save(createInitialLink(product, componentC, "3.0"));
        });

        List<ProductFeature> features = List.of(
                createFeatureDto(componentA, "50"), // Update
                createFeatureDto(componentB, "true") // Create
        ); // Component C missing -> Delete

        mockMvc.perform(put("/api/v1/products/{id}/features", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(features)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()", is(2)));

        txHelper.doInTransaction(() -> {
            List<ProductFeatureLink> finalLinks = linkRepository.findByProductId(product.getId());
            assertThat(finalLinks.stream().map(l -> l.getFeatureComponent().getId()).collect(Collectors.toSet()))
                    .containsExactlyInAnyOrder(componentA.getId(), componentB.getId());
            ProductFeatureLink linkA = finalLinks.stream()
                    .filter(l -> l.getFeatureComponent().getId().equals(componentA.getId()))
                    .findFirst().orElseThrow();
            assertThat(linkA.getFeatureValue()).isEqualTo("50");
            assertThat(finalLinks.stream().anyMatch(l -> l.getFeatureComponent().getId().equals(componentC.getId()))).isFalse();
        });
    }

    // =================================================================
    // 3. ERROR HANDLING (Validation)
    // =================================================================

    @Test
    void shouldReturn400OnSyncWithInvalidFeatureValueType() throws Exception {
        List<ProductFeature> features = List.of(createFeatureDto(componentB, "Not a boolean"));

        mockMvc.perform(put("/api/v1/products/{id}/features", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(features)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("must be 'true' or 'false' for BOOLEAN")));
    }

    @Test
    void shouldReturn404OnSyncWithNonExistentProduct() throws Exception {
        List<ProductFeature> features = List.of(createFeatureDto(componentA, "1"));

        mockMvc.perform(put("/api/v1/products/99999/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(features)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Product not found")));
    }
}