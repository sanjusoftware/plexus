package com.bankengine.catalog;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.dto.ProductRequest;
import com.bankengine.catalog.dto.ProductBundleRequest;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductBundleRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.pricing.TestTransactionHelper;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.bankengine.test.config.WithMockRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class CatalogDisplayIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductBundleRepository bundleRepository;
    @Autowired private ProductTypeRepository productTypeRepository;
    @Autowired private TestTransactionHelper txHelper;

    private static Long PRODUCT_TYPE_ID;
    private static final String DISPLAY_ROLE = "CUSTOMER_DISPLAY_READER";

    @BeforeAll
    static void setupRoles(@Autowired TestTransactionHelper txHelperStatic,
                           @Autowired ProductTypeRepository productTypeRepoStatic) {
        seedBaseRoles(txHelperStatic, Map.of(
            DISPLAY_ROLE, Set.of("catalog:display:read")
        ));

        TenantContextHolder.setBankId(TEST_BANK_ID);
        try {
            txHelperStatic.doInTransaction(() -> {
                ProductType pt = new ProductType();
                pt.setName("Checking Type");
                PRODUCT_TYPE_ID = productTypeRepoStatic.save(pt).getId();
            });
        } finally {
            TenantContextHolder.clear();
        }
    }

    @AfterEach
    void tearDown() {
        txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            bundleRepository.deleteAll();
            productRepository.deleteAll();
        });
    }

    @Test
    @WithMockRole(roles = {DISPLAY_ROLE})
    void shouldOnlyReturnActiveProducts() throws Exception {
        txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            ProductType type = productTypeRepository.findById(PRODUCT_TYPE_ID).get();

            Product activeProduct = new Product();
            activeProduct.setName("Active Product");
            activeProduct.setStatus("ACTIVE");
            activeProduct.setBankId(TEST_BANK_ID);
            activeProduct.setProductType(type);
            activeProduct.setEffectiveDate(LocalDate.now().minusDays(1));
            activeProduct.setCategory("RETAIL");
            productRepository.save(activeProduct);

            Product draftProduct = new Product();
            draftProduct.setName("Draft Product");
            draftProduct.setStatus("DRAFT");
            draftProduct.setBankId(TEST_BANK_ID);
            draftProduct.setProductType(type);
            draftProduct.setEffectiveDate(LocalDate.now());
            draftProduct.setCategory("RETAIL");
            productRepository.save(draftProduct);

            Product futureProduct = new Product();
            futureProduct.setName("Future Product");
            futureProduct.setStatus("ACTIVE");
            futureProduct.setBankId(TEST_BANK_ID);
            futureProduct.setProductType(type);
            futureProduct.setEffectiveDate(LocalDate.now().plusDays(1));
            futureProduct.setCategory("RETAIL");
            productRepository.save(futureProduct);
        });

        mockMvc.perform(get("/api/v1/catalog/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Active Product"));
    }

    @Test
    @WithMockRole(roles = {DISPLAY_ROLE})
    void shouldReturnProductDetailsOnlyIfActive() throws Exception {
        Long activeId = txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            ProductType type = productTypeRepository.findById(PRODUCT_TYPE_ID).get();
            Product p = new Product();
            p.setName("Active Product");
            p.setStatus("ACTIVE");
            p.setBankId(TEST_BANK_ID);
            p.setProductType(type);
            p.setEffectiveDate(LocalDate.now());
            p.setCategory("RETAIL");
            return productRepository.save(p).getId();
        });

        Long draftId = txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            ProductType type = productTypeRepository.findById(PRODUCT_TYPE_ID).get();
            Product p = new Product();
            p.setName("Draft Product");
            p.setStatus("DRAFT");
            p.setBankId(TEST_BANK_ID);
            p.setProductType(type);
            p.setEffectiveDate(LocalDate.now());
            p.setCategory("RETAIL");
            return productRepository.save(p).getId();
        });

        mockMvc.perform(get("/api/v1/catalog/products/{id}", activeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Active Product"));

        mockMvc.perform(get("/api/v1/catalog/products/{id}", draftId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockRole(roles = {DISPLAY_ROLE})
    void shouldOnlyReturnActiveBundles() throws Exception {
        txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);

            ProductBundle activeBundle = new ProductBundle();
            activeBundle.setName("Active Bundle");
            activeBundle.setCode("B1");
            activeBundle.setStatus(ProductBundle.BundleStatus.ACTIVE);
            activeBundle.setBankId(TEST_BANK_ID);
            activeBundle.setEligibilitySegment("RETAIL");
            activeBundle.setActivationDate(LocalDate.now().minusDays(1));
            bundleRepository.save(activeBundle);

            ProductBundle draftBundle = new ProductBundle();
            draftBundle.setName("Draft Bundle");
            draftBundle.setCode("B2");
            draftBundle.setStatus(ProductBundle.BundleStatus.DRAFT);
            draftBundle.setBankId(TEST_BANK_ID);
            draftBundle.setEligibilitySegment("RETAIL");
            draftBundle.setActivationDate(LocalDate.now());
            bundleRepository.save(draftBundle);

            ProductBundle futureBundle = new ProductBundle();
            futureBundle.setName("Future Bundle");
            futureBundle.setCode("B3");
            futureBundle.setStatus(ProductBundle.BundleStatus.ACTIVE);
            futureBundle.setBankId(TEST_BANK_ID);
            futureBundle.setEligibilitySegment("RETAIL");
            futureBundle.setActivationDate(LocalDate.now().plusDays(1));
            bundleRepository.save(futureBundle);
        });

        mockMvc.perform(get("/api/v1/catalog/bundles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Active Bundle"));
    }

    @Test
    @WithMockRole(roles = {DISPLAY_ROLE})
    void shouldReturnBundleDetailsOnlyIfActive() throws Exception {
        Long activeId = txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            ProductBundle b = new ProductBundle();
            b.setName("Active Bundle");
            b.setCode("B1");
            b.setStatus(ProductBundle.BundleStatus.ACTIVE);
            b.setBankId(TEST_BANK_ID);
            b.setEligibilitySegment("RETAIL");
            b.setActivationDate(LocalDate.now());
            return bundleRepository.save(b).getId();
        });

        Long draftId = txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            ProductBundle b = new ProductBundle();
            b.setName("Draft Bundle");
            b.setCode("B2");
            b.setStatus(ProductBundle.BundleStatus.DRAFT);
            b.setBankId(TEST_BANK_ID);
            b.setEligibilitySegment("RETAIL");
            b.setActivationDate(LocalDate.now());
            return bundleRepository.save(b).getId();
        });

        mockMvc.perform(get("/api/v1/catalog/bundles/{id}", activeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Active Bundle"));

        mockMvc.perform(get("/api/v1/catalog/bundles/{id}", draftId))
                .andExpect(status().isNotFound());
    }
}
