package com.bankengine.catalog;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.dto.ProductBundleRequest;
import com.bankengine.catalog.dto.VersionRequest;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.BundleProductLinkRepository;
import com.bankengine.catalog.repository.ProductBundleRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.CategoryConflictRule;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.pricing.TestTransactionHelper;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.repository.BundlePricingLinkRepository;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.bankengine.test.config.WithMockRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static com.bankengine.common.util.CodeGeneratorUtil.generateValidCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductBundleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestTransactionHelper txHelper;
    @Autowired
    private BankConfigurationRepository bankConfigRepository;
    @Autowired
    private ProductBundleRepository productBundleRepository;
    @Autowired
    private BundleProductLinkRepository bundleProductLinkRepository;
    @Autowired
    private BundlePricingLinkRepository bundlePricingLinkRepository;
    @Autowired
    private ProductRepository productRepository;

    private Long retailProductId;
    private Long wealthProductId;
    private Long existingBundleId;

    private static final String BUNDLE_ADMIN = "BUNDLE_TEST_ADMIN";
    private static final String BUNDLE_UPDATER = "BUNDLE_UPDATER_ROLE";
    private static final String BUNDLE_ACTIVATOR = "BUNDLE_ACTIVATOR_ROLE";

    @BeforeAll
    static void initSecurity(@Autowired TestTransactionHelper tx) {
        seedBaseRoles(tx, Map.of(
                BUNDLE_ADMIN, Set.of("catalog:bundle:create", "catalog:bundle:read",
                        "catalog:bundle:activate", "catalog:bundle:delete"),
                BUNDLE_UPDATER, Set.of("catalog:bundle:update"),
                BUNDLE_ACTIVATOR, Set.of("catalog:bundle:activate", "catalog:bundle:delete")
        ));
    }

    @BeforeEach
    void setupData() {
        txHelper.doInTransaction(() -> {
            BankConfiguration config = bankConfigRepository.findByBankIdUnfiltered(TEST_BANK_ID)
                    .orElseGet(() -> {
                        BankConfiguration newConfig = new BankConfiguration();
                        newConfig.setBankId(TEST_BANK_ID);
                        return newConfig;
                    });
            config.setCategoryConflictRules(new ArrayList<>(List.of(
                    new CategoryConflictRule("RETAIL", "WEALTH")
            )));
            config.setIssuerUrl(TEST_BANK_ISS_URL);
            config.setClientId(TEST_BANK_CLINET_ID);
            bankConfigRepository.save(config);

            ProductBundle bundle = txHelper.setupFullBundleWithPricing(
                    "Integration Test Bundle",
                    "Retail Savings",
                    BigDecimal.valueOf(-10.00),
                    PriceValue.ValueType.DISCOUNT_ABSOLUTE,
                    VersionableEntity.EntityStatus.DRAFT
            );

            existingBundleId = bundle.getId();
            txHelper.flushAndClear();

            ProductBundle refreshedBundle = productBundleRepository.findById(existingBundleId).orElseThrow();
            retailProductId = refreshedBundle.getContainedProducts().getFirst().getProduct().getId();

            ProductType type = txHelper.getOrCreateProductType("SAVINGS");
            wealthProductId = txHelper.getOrCreateProduct("Wealth Investment", type, "WEALTH").getId();
        });
        txHelper.flushAndClear();
    }

    @AfterEach
    void tearDown() {
        txHelper.doInTransaction(() -> {
            bundlePricingLinkRepository.deleteAllInBatch();
            bundleProductLinkRepository.deleteAllInBatch();
            productBundleRepository.deleteAllInBatch();
            TenantContextHolder.clear();
        });
    }

    // --- 1. CREATE OPERATIONS ---

    @Test
    @WithMockRole(roles = BUNDLE_ADMIN)
    @DisplayName("createBundle - Should successfully create a DRAFT bundle with a unique product")
    void createBundle_ShouldSucceed() throws Exception {
        String newProductCode = txHelper.doInTransaction(() -> {
            ProductType type = txHelper.getOrCreateProductType("SAVINGS");
            return txHelper.getOrCreateProduct("Unique Product " + UUID.randomUUID(), type, "RETAIL").getCode();
        });

        ProductBundleRequest request = createBaseRequest("New Bundle");
        request.setProducts(List.of(createItem(newProductCode, true)));

        mockMvc.perform(postWithCsrf("/api/v1/bundles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(1));
    }

    // --- 2. UPDATE OPERATIONS (PATCH) ---

    @Test
    @WithMockRole(roles = BUNDLE_UPDATER)
    @DisplayName("updateBundle - Should update DRAFT bundle in-place without versioning")
    void updateBundle_ShouldSucceed() throws Exception {
        ProductBundleRequest updateRequest = new ProductBundleRequest();
        updateRequest.setName("Updated In-Place Name");

        mockMvc.perform(patchWithCsrf("/api/v1/bundles/{id}", existingBundleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existingBundleId))
                .andExpect(jsonPath("$.name").value("Updated In-Place Name"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    // --- 3. VERSIONING OPERATIONS (CLONE) ---

    @Test
    @WithMockRole(roles = BUNDLE_ADMIN)
    @DisplayName("versionBundle - Should reset to version 1 when a new code is provided")
    void versionBundle_ShouldResetVersionOnNewCode() throws Exception {
        VersionRequest versionRequest = new VersionRequest();
        versionRequest.setNewName("Cloned Premium Bundle");
        versionRequest.setNewCode("NEW-LINEAGE-CODE");

        mockMvc.perform(postWithCsrf("/api/v1/bundles/{id}/create-new-version", existingBundleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(versionRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cloned Premium Bundle"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @WithMockRole(roles = {BUNDLE_ADMIN, BUNDLE_ACTIVATOR})
    @DisplayName("versionBundle - Should increment version to 2 and create DRAFT revision")
    void versionBundle_ShouldIncrementVersion() throws Exception {
        // 1. Fetch the bundle and ACTIVATE it first
        txHelper.doInTransaction(() -> {
            ProductBundle bundle = productBundleRepository.findById(existingBundleId).orElseThrow();
            bundle.getContainedProducts().forEach(link ->
                    link.getProduct().setStatus(VersionableEntity.EntityStatus.ACTIVE));
            bundle.setStatus(VersionableEntity.EntityStatus.ACTIVE);
            productBundleRepository.save(bundle);
        });

        String originalCode = txHelper.doInTransaction(() ->
                productBundleRepository.findById(existingBundleId)
                        .orElseThrow()
                        .getCode()
        );

        VersionRequest versionRequest = new VersionRequest();
        versionRequest.setNewName("Premium Bundle V2");

        // 2. Perform versioning
        mockMvc.perform(postWithCsrf("/api/v1/bundles/{id}/create-new-version", existingBundleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(versionRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.code").value(originalCode))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        // 3. Verify original remains active until the revision is activated
        mockMvc.perform(get("/api/v1/bundles/{id}", existingBundleId))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // --- 4. LIFECYCLE OPERATIONS (ACTIVATE/ARCHIVE) ---

    @Test
    @WithMockRole(roles = BUNDLE_ACTIVATOR)
    @DisplayName("activateBundle - Should transition status from DRAFT to ACTIVE")
    void activateBundle_ShouldSucceed() throws Exception {
        // Ensure products are ACTIVE first (as per service requirement)
        txHelper.doInTransaction(() -> {
            productRepository.findById(retailProductId).ifPresent(p -> p.setStatus(VersionableEntity.EntityStatus.ACTIVE));
        });

        mockMvc.perform(postWithCsrf("/api/v1/bundles/{id}/activate", existingBundleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockRole(roles = BUNDLE_ACTIVATOR)
    @DisplayName("archiveBundle - Should soft-delete the bundle by setting ARCHIVED status")
    void archiveBundle_ShouldSucceed() throws Exception {
        mockMvc.perform(deleteWithCsrf("/api/v1/bundles/{id}", existingBundleId))
                .andExpect(status().isNoContent());

        txHelper.doInTransaction(() -> {
            ProductBundle archived = productBundleRepository.findById(existingBundleId).orElseThrow();
            assertEquals(VersionableEntity.EntityStatus.ARCHIVED, archived.getStatus());
            assertNotNull(archived.getExpiryDate());
        });
    }

    // --- 5. BUSINESS CONSTRAINT VALIDATIONS ---

    @Test
    @WithMockRole(roles = BUNDLE_ADMIN)
    @DisplayName("Validation - Should fail with specific message on category conflict")
    void createBundle_ShouldFail_OnCategoryConflict() throws Exception {
        // Create two fresh products that aren't in any bundle yet
        String retailProductCode = txHelper.doInTransaction(() -> {
            ProductType type = txHelper.getOrCreateProductType("SAVINGS");
            return txHelper.getOrCreateProduct("Retail P", type, "RETAIL").getCode();
        });
        String wealthProductCode = txHelper.doInTransaction(() -> {
            ProductType type = txHelper.getOrCreateProductType("INVEST");
            return txHelper.getOrCreateProduct("Wealth P", type, "WEALTH").getCode();
        });

        ProductBundleRequest request = createBaseRequest("Conflict Bundle");
        request.setProducts(List.of(
                createItem(retailProductCode, true),
                createItem(wealthProductCode, false)
        ));

        String expectedErrorMessage = String.format(
                "Conflict: Category 'WEALTH' cannot be bundled with category 'RETAIL' for bank %s.",
                TEST_BANK_ID);

        mockMvc.perform(postWithCsrf("/api/v1/bundles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(expectedErrorMessage));
    }

    @Test
    @WithMockRole(roles = BUNDLE_ADMIN)
    @DisplayName("Validation - Should fail when more than one product is marked as Main Account")
    void createBundle_ShouldFail_WhenMultipleMainProductsExist() throws Exception {
        String retailProductCode = txHelper.doInTransaction(() -> productRepository.findById(retailProductId).orElseThrow().getCode());
        String wealthProductCode = txHelper.doInTransaction(() -> productRepository.findById(wealthProductId).orElseThrow().getCode());
        ProductBundleRequest request = createBaseRequest("Invalid Multi Main");
        request.setProducts(List.of(
                createItem(retailProductCode, true),
                createItem(wealthProductCode, true)
        ));

        mockMvc.perform(postWithCsrf("/api/v1/bundles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A bundle can only have 1 Main Account item."));
    }

    @Test
    @WithMockUser
    @DisplayName("Security - Should return 403 when user lacks authorities")
    void security_ShouldFail_WhenUnauthorized() throws Exception {
        mockMvc.perform(deleteWithCsrf("/api/v1/bundles/{id}", existingBundleId))
                .andExpect(status().isForbidden());
    }

    // --- HELPERS ---

    private ProductBundleRequest createBaseRequest(String name) {
        ProductBundleRequest request = new ProductBundleRequest();
        request.setName(name);
        request.setCode(generateValidCode(name));
        request.setActivationDate(LocalDate.now().plusDays(7));
        request.setTargetCustomerSegments("RETAIL");
        return request;
    }

    private ProductBundleRequest.BundleProduct createItem(String code, boolean main) {
        ProductBundleRequest.BundleProduct item = new ProductBundleRequest.BundleProduct();
        item.setProductCode(code);
        item.setMainAccount(main);
        return item;
    }
}