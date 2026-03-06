package com.bankengine.catalog;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.pricing.TestTransactionHelper;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.bankengine.test.config.WithMockRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ProductSearchIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductTypeRepository productTypeRepository;
    @Autowired private TestTransactionHelper txHelper;

    private static Long EXISTING_PRODUCT_TYPE_ID;
    private final String PRODUCT_API_BASE = "/api/v1/products";

    public static final String ROLE_PREFIX = "PSIT_";
    private static final String ADMIN_ROLE = ROLE_PREFIX + "CATALOG_ADMIN";

    @BeforeAll
    static void setupRoles(@Autowired TestTransactionHelper txHelperStatic,
                           @Autowired ProductTypeRepository productTypeRepoStatic) {

        seedBaseRoles(txHelperStatic, Map.of(
                ADMIN_ROLE, Set.of("catalog:product:read")
        ));

        try {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            txHelperStatic.doInTransaction(() -> {
                productTypeRepoStatic.deleteAllInBatch();
                ProductType baseType = ProductType.builder().name("General Banking").code("GEN").bankId(TEST_BANK_ID).build();
                EXISTING_PRODUCT_TYPE_ID = productTypeRepoStatic.save(baseType).getId();
            });
        } finally {
            TenantContextHolder.clear();
        }
    }

    @AfterEach
    void tearDown() {
        txHelper.doInTransaction(() -> productRepository.deleteAllInBatch());
    }

    // --- Helpers ---

    private void setupMultipleProductsForSearch() {
        txHelper.doInTransaction(() -> {
            ProductType generalType = productTypeRepository.findById(EXISTING_PRODUCT_TYPE_ID).orElseThrow();
            ProductType cardType = productTypeRepository.findByBankIdAndCode(TEST_BANK_ID, "CRD")
                    .orElseGet(() -> productTypeRepository.save(ProductType.builder().name("Cards").code("CRD").bankId(TEST_BANK_ID).build()));

            // 1. Draft Product
            productRepository.save(Product.builder()
                    .name("Draft Checking").code("CODE_D1").version(1).productType(generalType)
                    .category("RETAIL").status(VersionableEntity.EntityStatus.DRAFT).bankId(TEST_BANK_ID).build());

            // 2. Active Product (Matching "Checking")
            productRepository.save(Product.builder()
                    .name("Active Checking").code("CODE_A1").version(1).productType(generalType)
                    .category("RETAIL").status(VersionableEntity.EntityStatus.ACTIVE).bankId(TEST_BANK_ID)
                    .activationDate(LocalDate.now().minusDays(5)).build());

            // 3. Inactive Savings
            productRepository.save(Product.builder()
                    .name("Inactive Savings").code("CODE_I1").version(1).productType(generalType)
                    .category("RETAIL").status(VersionableEntity.EntityStatus.INACTIVE).bankId(TEST_BANK_ID).build());

            // 4. Active Card (Different Type)
            productRepository.save(Product.builder()
                    .name("Premium Card").code("CODE_A2").version(1).productType(cardType)
                    .category("RETAIL").status(VersionableEntity.EntityStatus.ACTIVE).bankId(TEST_BANK_ID)
                    .activationDate(LocalDate.now().minusDays(1)).build());
        });
    }

    // --- Tests ---

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturnAllProductsWhenNoFilterCriteriaAreProvided() throws Exception {
        setupMultipleProductsForSearch();
        mockMvc.perform(get(PRODUCT_API_BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldFilterProductsByStatusAndName() throws Exception {
        setupMultipleProductsForSearch();
        mockMvc.perform(get(PRODUCT_API_BASE)
                        .param("status", "ACTIVE")
                        .param("name", "Checking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Active Checking"));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldFilterProductsByProductType() throws Exception {
        setupMultipleProductsForSearch();
        Long cardTypeId = txHelper.doInTransaction(() ->
                productTypeRepository.findByBankIdAndCode(TEST_BANK_ID, "CRD").get().getId());

        mockMvc.perform(get(PRODUCT_API_BASE)
                        .param("productTypeId", cardTypeId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Premium Card"));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturnZeroProductsWhenNoCriteriaMatch() throws Exception {
        setupMultipleProductsForSearch();
        mockMvc.perform(get(PRODUCT_API_BASE).param("status", "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldFilterProductsByEffectiveDateRange() throws Exception {
        txHelper.doInTransaction(() -> {
            ProductType type = productTypeRepository.findById(EXISTING_PRODUCT_TYPE_ID).get();
            productRepository.save(Product.builder()
                    .name("Future Product").code("F1").version(1).productType(type).bankId(TEST_BANK_ID)
                    .category("RETAIL").status(VersionableEntity.EntityStatus.DRAFT)
                    .activationDate(LocalDate.now().plusDays(10)).build());

            productRepository.save(Product.builder()
                    .name("Past Product").code("P1").version(1).productType(type).bankId(TEST_BANK_ID)
                    .category("RETAIL").status(VersionableEntity.EntityStatus.ACTIVE)
                    .activationDate(LocalDate.now().minusDays(10)).build());
        });

        mockMvc.perform(get(PRODUCT_API_BASE)
                        .param("activationDateFrom", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Future Product"));
    }
}