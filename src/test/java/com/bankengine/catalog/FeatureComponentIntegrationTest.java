package com.bankengine.catalog;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.dto.FeatureComponentRequest;
import com.bankengine.catalog.dto.VersionRequest;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.FeatureComponentRepository;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.common.util.CodeGeneratorUtil;
import com.bankengine.pricing.TestTransactionHelper;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.bankengine.test.config.WithMockRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static com.bankengine.common.util.CodeGeneratorUtil.generateValidCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class FeatureComponentIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FeatureComponentRepository featureComponentRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductTypeRepository productTypeRepository;
    @Autowired private ProductFeatureLinkRepository linkRepository;
    @Autowired private TestTransactionHelper txHelper;

    private static Product sharedProduct;

    public static final String ROLE_PREFIX = "FCIT_";
    private static final String ADMIN_ROLE = ROLE_PREFIX + "CATALOG_ADMIN";
    private static final String READER_ROLE = ROLE_PREFIX + "CATALOG_READER";
    private static final String UNAUTHORIZED_ROLE = ROLE_PREFIX + "UNAUTHORIZED_ROLE";

    @BeforeAll
    static void setupCommittedData(@Autowired TestTransactionHelper txHelperStatic,
                                   @Autowired ProductRepository productRepoStatic,
                                   @Autowired ProductTypeRepository productTypeRepoStatic,
                                   @Autowired FeatureComponentRepository featureRepoStatic,
                                   @Autowired ProductFeatureLinkRepository linkRepoStatic) {

        seedBaseRoles(txHelperStatic, Map.of(
                ADMIN_ROLE, Set.of("catalog:feature:create", "catalog:feature:read", "catalog:feature:update", "catalog:feature:delete", "catalog:feature:activate"),
                READER_ROLE, Set.of("catalog:feature:read"),
                UNAUTHORIZED_ROLE, Set.of("some:other:permission")
        ));

        // 2. Setup Shared Entities for Linking
        try {
            TenantContextHolder.setBankId(TEST_BANK_ID);

            txHelperStatic.doInTransaction(() -> {
                linkRepoStatic.deleteAll();
                featureRepoStatic.deleteAll();
                productRepoStatic.deleteAll();
                productTypeRepoStatic.deleteAll();

                ProductType type = new ProductType();
                type.setName("Test Type for Link");
                type.setCode("TTFL");
                type.setBankId(TEST_BANK_ID);
                ProductType savedType = productTypeRepoStatic.save(type);

                String productName = "Link Test Product";
                Product product = new Product();
                product.setName(productName);
                product.setCode(generateValidCode(productName));
                product.setVersion(1);
                product.setBankId(TEST_BANK_ID);
                product.setActivationDate(LocalDate.now());
                product.setStatus(VersionableEntity.EntityStatus.ACTIVE);
                product.setProductType(savedType);
                product.setCategory("RETAIL");
                sharedProduct = productRepoStatic.save(product);
            });
            txHelperStatic.flushAndClear();
        } finally {
            TenantContextHolder.clear();
        }
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.setBankId(TEST_BANK_ID);
        txHelper.doInTransaction(() -> {
            linkRepository.deleteAllInBatch();
            featureComponentRepository.deleteAllInBatch();
        });
        txHelper.flushAndClear();
    }

    private FeatureComponentRequest newFeatureComponentRequest(String name) {
        FeatureComponentRequest dto = new FeatureComponentRequest();
        dto.setName(name);
        dto.setCode(generateValidCode(name));
        dto.setDataType("STRING");
        return dto;
    }

    private FeatureComponent createFeatureComponentInDb(String name) {
        String code = CodeGeneratorUtil.sanitizeAsCode(generateValidCode(name));
        return txHelper.doInTransaction(() -> featureComponentRepository.findByBankIdAndCodeAndVersion(TEST_BANK_ID, code, 1)
                .orElseGet(() -> featureComponentRepository.save(
                        FeatureComponent.builder()
                                .name(name)
                                .code(code)
                                .version(1)
                                .dataType(FeatureComponent.DataType.STRING)
                                .status(VersionableEntity.EntityStatus.DRAFT)
                                .bankId(TEST_BANK_ID)
                                .build())
                ));
    }

    private FeatureComponent createFeatureComponentInDb(String name, String code, int version, VersionableEntity.EntityStatus status) {
        String sanitizedCode = CodeGeneratorUtil.sanitizeAsCode(code);
        return txHelper.doInTransaction(() -> featureComponentRepository.save(
                FeatureComponent.builder()
                        .name(name)
                        .code(sanitizedCode)
                        .version(version)
                        .dataType(FeatureComponent.DataType.STRING)
                        .status(status)
                        .bankId(TEST_BANK_ID)
                        .build()
        ));
    }

    // --- 1. CREATE TESTS ---

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn403WhenCreatingFeatureWithoutPermission() throws Exception {
        mockMvc.perform(postWithCsrf("/api/v1/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newFeatureComponentRequest("ForbiddenFeature"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldCreateFeatureAndReturn201() throws Exception {
        String name = "PremiumSupport";
        FeatureComponentRequest request = newFeatureComponentRequest(name);
        String code = request.getCode().toUpperCase().replace(" ", "_");
        mockMvc.perform(postWithCsrf("/api/v1/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is(name)))
                .andExpect(jsonPath("$.dataType", is("STRING")))
                .andExpect(jsonPath("$.id").isNumber());

        // Verify DB Tenancy & Auditing
        txHelper.doInTransaction(() -> {
            FeatureComponent fc = featureComponentRepository.findByBankIdAndCodeAndVersion(TEST_BANK_ID, code, 1).orElseThrow();
            assertThat(fc.getBankId()).isEqualTo(TEST_BANK_ID);
            assertThat(fc.getCreatedBy()).isNotEqualTo("SYSTEM"); // Should be mock user
        });
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn400OnCreateWithInvalidDataType() throws Exception {
        FeatureComponentRequest dto = newFeatureComponentRequest("BadTypeFeature");
        dto.setDataType("XYZ");

        mockMvc.perform(postWithCsrf("/api/v1/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Invalid data type provided: XYZ")));
    }

    // --- 2. RETRIEVE TESTS ---

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void shouldReturn403WhenReadingFeatureWithoutPermission() throws Exception {
        FeatureComponent savedComponent = createFeatureComponentInDb("ForbiddenFeature");
        mockMvc.perform(get("/api/v1/features/{id}", savedComponent.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn200AndFeatureById() throws Exception {
        FeatureComponent savedComponent = createFeatureComponentInDb("ATMWithdrawals");

        mockMvc.perform(get("/api/v1/features/{id}", savedComponent.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("ATMWithdrawals")))
                .andExpect(jsonPath("$.id", is(savedComponent.getId().intValue())));
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn404WhenGettingNonExistentFeature() throws Exception {
        mockMvc.perform(get("/api/v1/features/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    // --- 3. UPDATE TESTS ---

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn403WhenUpdatingFeatureWithoutPermission() throws Exception {
        FeatureComponent savedComponent = createFeatureComponentInDb("ForbiddenFeature");
        FeatureComponentRequest updateDto = newFeatureComponentRequest("NewName");

        mockMvc.perform(patchWithCsrf("/api/v1/features/{id}", savedComponent.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldUpdateFeatureAndReturn200() throws Exception {
        FeatureComponent savedComponent = createFeatureComponentInDb("OldName");
        FeatureComponentRequest updateDto = new FeatureComponentRequest();
        updateDto.setName("NewName");
        updateDto.setCode(savedComponent.getCode()); // Keep existing code
        updateDto.setDataType("BOOLEAN");

        mockMvc.perform(patchWithCsrf("/api/v1/features/{id}", savedComponent.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("NewName")))
                .andExpect(jsonPath("$.dataType", is("BOOLEAN")));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn409WhenUpdatingFeatureCodeToCodeUsedByAnotherFeature() throws Exception {
        FeatureComponent source = createFeatureComponentInDb("SourceFeature");
        FeatureComponent conflicting = createFeatureComponentInDb("ConflictingFeature");

        FeatureComponentRequest updateDto = new FeatureComponentRequest();
        updateDto.setName("Renamed Feature");
        updateDto.setCode(conflicting.getCode());
        updateDto.setDataType("STRING");

        mockMvc.perform(patchWithCsrf("/api/v1/features/{id}", source.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Feature code '" + conflicting.getCode() + "' is already used by another feature component.")));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn404OnUpdateNonExistentFeature() throws Exception {
        mockMvc.perform(patchWithCsrf("/api/v1/features/99999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newFeatureComponentRequest("Test"))))
                .andExpect(status().isNotFound());
    }

    // --- 4. DELETE TESTS ---

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn403WhenDeletingFeatureWithoutPermission() throws Exception {
        FeatureComponent savedComponent = createFeatureComponentInDb("ForbiddenFeature");
        mockMvc.perform(deleteWithCsrf("/api/v1/features/{id}", savedComponent.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Branch: DRAFT Status -> Physical Deletion")
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldDeleteFeatureAndReturn204() throws Exception {
        FeatureComponent savedComponent = createFeatureComponentInDb("DeletableFeature");
        Long idToDelete = savedComponent.getId();

        mockMvc.perform(deleteWithCsrf("/api/v1/features/{id}", idToDelete))
                .andExpect(status().isNoContent());

        // Assert physical removal via API and DB
        mockMvc.perform(get("/api/v1/features/{id}", idToDelete)).andExpect(status().isNotFound());
        txHelper.doInTransaction(() -> assertThat(featureComponentRepository.findById(idToDelete)).isEmpty());
    }

    @Test
    @DisplayName("Branch: ACTIVE Status -> Logical Archive")
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldArchiveFeatureInsteadOfDelete() throws Exception {
        FeatureComponent savedComponent = createFeatureComponentInDb("ArchivableFeature");
        txHelper.doInTransaction(() -> {
            savedComponent.setStatus(VersionableEntity.EntityStatus.ACTIVE);
            featureComponentRepository.save(savedComponent);
        });

        mockMvc.perform(deleteWithCsrf("/api/v1/features/{id}", savedComponent.getId()))
                .andExpect(status().isNoContent());

        // Verify logical existence with ARCHIVED status
        txHelper.doInTransaction(() -> {
            FeatureComponent fc = featureComponentRepository.findById(savedComponent.getId())
                    .orElseThrow(() -> new NoSuchElementException("Active feature should be archived, not physically deleted!"));
            assertThat(fc.getStatus()).isEqualTo(VersionableEntity.EntityStatus.ARCHIVED);
        });
    }

    @Test
    @DisplayName("Branch: Dependency Violation -> 409 Conflict")
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn409WhenDeletingLinkedFeature() throws Exception {
        FeatureComponent linkedComponent = createFeatureComponentInDb("LinkedFeature");

        txHelper.doInTransaction(() -> {
            ProductFeatureLink link = new ProductFeatureLink();
            link.setFeatureComponent(linkedComponent);
            link.setProduct(sharedProduct);
            link.setFeatureValue("Default Value");
            linkRepository.save(link);
        });

        mockMvc.perform(deleteWithCsrf("/api/v1/features/{id}", linkedComponent.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Cannot delete feature as it is linked to 1 product(s).")));
    }

    // --- 5. LIFECYCLE & STATE MACHINE TESTS ---

    @Test
    @DisplayName("Activation - Should transition DRAFT to ACTIVE")
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldActivateFeature() throws Exception {
        FeatureComponent feature = createFeatureComponentInDb("ToActivate");

        mockMvc.perform(postWithCsrf("/api/v1/features/{id}/activate", feature.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(feature.getId().intValue())))
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        txHelper.doInTransaction(() -> {
            assertThat(featureComponentRepository.findById(feature.getId()).orElseThrow().getStatus())
                    .isEqualTo(VersionableEntity.EntityStatus.ACTIVE);
        });
    }

    @Test
    @DisplayName("Update Constraint - Should return 409 when updating an already ACTIVE feature")
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldFailToUpdateActiveFeature() throws Exception {
        FeatureComponent activeFeature = createFeatureComponentInDb("LockedFeature");
        txHelper.doInTransaction(() -> {
            activeFeature.setStatus(VersionableEntity.EntityStatus.ACTIVE);
            featureComponentRepository.save(activeFeature);
        });

        FeatureComponentRequest request = newFeatureComponentRequest("AttemptedChange");
        request.setCode(activeFeature.getCode());

        mockMvc.perform(patchWithCsrf("/api/v1/features/{id}", activeFeature.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Operation allowed only on DRAFT status.")));
    }

    // --- 6. VERSIONING TESTS ---

    @Test
    @DisplayName("Versioning - Should create new ACTIVE version from ACTIVE feature")
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldVersionFeatureSuccessfully() throws Exception {
        FeatureComponent source = createFeatureComponentInDb("BaseFeature");
        final String code = source.getCode();
        txHelper.doInTransaction(() -> {
            FeatureComponent fc = featureComponentRepository.findById(source.getId()).orElseThrow();
            fc.setStatus(VersionableEntity.EntityStatus.ACTIVE);
            fc.setVersion(1);
            featureComponentRepository.save(fc);
        });

        VersionRequest vRequest = new VersionRequest();

        mockMvc.perform(postWithCsrf("/api/v1/features/{id}/create-new-version", source.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.code", is(code)))
                .andExpect(jsonPath("$.version", is(2)))
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        txHelper.doInTransaction(() -> {
            FeatureComponent v2 = featureComponentRepository.findByBankIdAndCodeAndVersion(TEST_BANK_ID, code, 2).orElseThrow();
            assertThat(v2.getStatus()).isEqualTo(VersionableEntity.EntityStatus.ACTIVE);

            FeatureComponent v1 = featureComponentRepository.findByBankIdAndCodeAndVersion(TEST_BANK_ID, code, 1).orElseThrow();
            assertThat(v1.getStatus()).isEqualTo(VersionableEntity.EntityStatus.ARCHIVED);
        });
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturnAllFeaturesByCodeRegardlessOfStatusWhenVersionIsNotProvided() throws Exception {
        String sharedCode = generateValidCode("SharedFeatureCode");
        createFeatureComponentInDb("Shared Feature V1", sharedCode, 1, VersionableEntity.EntityStatus.ACTIVE);
        createFeatureComponentInDb("Shared Feature V2", sharedCode, 2, VersionableEntity.EntityStatus.ARCHIVED);

        mockMvc.perform(get("/api/v1/features/code/{code}", sharedCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldFilterFeaturesByCodeEndpointWhenVersionIsProvided() throws Exception {
        String sharedCode = generateValidCode("VersionedFeatureCode");
        createFeatureComponentInDb("Versioned Feature V1", sharedCode, 1, VersionableEntity.EntityStatus.ACTIVE);
        createFeatureComponentInDb("Versioned Feature V2", sharedCode, 2, VersionableEntity.EntityStatus.ARCHIVED);

        mockMvc.perform(get("/api/v1/features/code/{code}", sharedCode)
                        .param("version", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].version", is(2)))
                .andExpect(jsonPath("$[0].status", is("ARCHIVED")));
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldSearchFeaturesUsingCodeVersionAndStatusQueryParameters() throws Exception {
        String searchCode = generateValidCode("SearchableFeature");
        createFeatureComponentInDb("Searchable Feature V1", searchCode, 1, VersionableEntity.EntityStatus.ACTIVE);
        createFeatureComponentInDb("Searchable Feature V2", searchCode, 2, VersionableEntity.EntityStatus.ARCHIVED);
        createFeatureComponentInDb("Other Feature", generateValidCode("OtherFeature"), 1, VersionableEntity.EntityStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/features")
                        .param("code", searchCode)
                        .param("version", "2")
                        .param("status", "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code", is(CodeGeneratorUtil.sanitizeAsCode(searchCode))))
                .andExpect(jsonPath("$[0].version", is(2)))
                .andExpect(jsonPath("$[0].status", is("ARCHIVED")));
    }

    // --- 7. MULTI-LINK DEPENDENCY CHECK ---

    @Test
    @DisplayName("Delete - Should show correct count of links in error message")
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldShowCorrectLinkCountIn409() throws Exception {
        FeatureComponent feature = createFeatureComponentInDb("HeavilyLinked");

        txHelper.doInTransaction(() -> {
            String productName = "Second Product";
            Product secondProduct = new Product();
            secondProduct.setName(productName);
            secondProduct.setCode(generateValidCode(productName));
            secondProduct.setStatus(VersionableEntity.EntityStatus.ACTIVE);
            secondProduct.setProductType(sharedProduct.getProductType()); // Reuse the same type
            secondProduct.setCategory("RETAIL");
            productRepository.save(secondProduct);

            ProductFeatureLink link1 = new ProductFeatureLink();
            link1.setFeatureComponent(feature);
            link1.setProduct(sharedProduct);
            link1.setFeatureValue("V1");

            ProductFeatureLink link2 = new ProductFeatureLink();
            link2.setFeatureComponent(feature);
            link2.setProduct(secondProduct); // Link to the NEW product
            link2.setFeatureValue("V2");

            linkRepository.saveAll(List.of(link1, link2));
        });

        mockMvc.perform(deleteWithCsrf("/api/v1/features/{id}", feature.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Cannot delete feature as it is linked to 2 product(s).")));
    }
}
