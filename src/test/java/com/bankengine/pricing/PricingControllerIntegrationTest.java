package com.bankengine.pricing;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.pricing.dto.BundlePriceRequest;
import com.bankengine.pricing.dto.PricingRequest;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.bankengine.test.config.WithMockRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class PricingControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestTransactionHelper txHelper;
    @Autowired private EntityManager entityManager;
    @Autowired private ProductTypeRepository productTypeRepository;

    public static final String ROLE_PREFIX = "PC_";
    private static final String PRICING_READER_ROLE = ROLE_PREFIX + "TEST_READER";
    private static final String BASE_URL = "/api/v1/pricing";

    @BeforeAll
    static void setupRoles(@Autowired TestTransactionHelper txHelperStatic) {
        seedBaseRoles(txHelperStatic, Map.of(
            PRICING_READER_ROLE, Set.of("pricing:calculate:read", "pricing:bundle:calculate:read")
        ));
    }

    @BeforeEach
    void setupMetadata() {
        txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            txHelper.setupCommittedMetadata();
        });
        entityManager.clear();
    }

    @Test
    @WithMockRole(roles = {PRICING_READER_ROLE})
    void calculateProductPrice_ShouldReturn200_WhenRequestIsValid() throws Exception {
        Long productId = txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);

            ProductType type = new ProductType();
            type.setName("Savings Type");
            type.setBankId(TEST_BANK_ID);
            Long typeId = productTypeRepository.save(type).getId();

            Long pId = txHelper.createProductInDb("Savings Account", typeId, "RETAIL");
            PricingComponent component = txHelper.createPricingComponentInDb("Monthly Fee");

            txHelper.linkProductToPricingComponent(pId, component.getId(), new BigDecimal("10.00"));
            return pId;
        });

        PricingRequest request = new PricingRequest();
        request.setProductId(productId);
        request.setCustomerSegment("RETAIL");
        request.setAmount(BigDecimal.valueOf(1000.0));
        request.setEffectiveDate(LocalDate.now());
        request.setCustomAttributes(Map.of("transactionAmount", new BigDecimal("1000")));

        mockMvc.perform(post(BASE_URL + "/calculate/product")
                        .header("X-Bank-Id", TEST_BANK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalChargeablePrice").exists())
                .andExpect(jsonPath("$.componentBreakdown[0].componentCode").value("Monthly Fee"));
    }

    @Test
    @WithMockRole(roles = {PRICING_READER_ROLE})
    void calculateBundlePrice_ShouldReturn200_WhenRequestIsValid() throws Exception {
        Map<String, Long> ids = txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);

            ProductType type = new ProductType();
            type.setName("Bundle Type");
            type.setBankId(TEST_BANK_ID);
            Long typeId = productTypeRepository.save(type).getId();

            Long p1Id = txHelper.createProductInDb("Product 1", typeId, "RETAIL");
            Long p2Id = txHelper.createProductInDb("Product 2", typeId, "RETAIL");

            PricingComponent component = txHelper.createPricingComponentInDb("Bundle Discount");
            txHelper.linkProductToPricingComponent(p1Id, component.getId(), new BigDecimal("10.00"));
            txHelper.linkProductToPricingComponent(p2Id, component.getId(), new BigDecimal("5.00"));

            ProductBundle bundle = txHelper.createBundleInDb("Super Salary Package");

            return Map.of("p1", p1Id, "p2", p2Id, "bundle", bundle.getId());
        });

        BundlePriceRequest request = new BundlePriceRequest();
        request.setProductBundleId(ids.get("bundle"));
        request.setCustomerSegment("RETAIL");

        BundlePriceRequest.ProductRequest pr1 = new BundlePriceRequest.ProductRequest();
        pr1.setProductId(ids.get("p1"));
        pr1.setAmount(BigDecimal.valueOf(1000));

        BundlePriceRequest.ProductRequest pr2 = new BundlePriceRequest.ProductRequest();
        pr2.setProductId(ids.get("p2"));
        pr2.setAmount(BigDecimal.valueOf(1000));

        request.setProducts(List.of(pr1, pr2));

        mockMvc.perform(post(BASE_URL + "/calculate/bundle")
                        .header("X-Bank-Id", TEST_BANK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productBundleId").value(ids.get("bundle")))
                .andExpect(jsonPath("$.grossTotalAmount").value(15.00))
                .andExpect(jsonPath("$.netTotalAmount").value(15.00));
    }

    @Test
    @WithMockRole(roles = {PRICING_READER_ROLE})
    void calculateBundlePrice_ShouldApplyBundleAdjustments_WhenAdjustmentExists() throws Exception {
        // 1. ARRANGE: Create products (summing to 15.00) and a bundle discount (-2.00)
        Map<String, Long> ids = txHelper.doInTransaction(() -> {
            TenantContextHolder.setBankId(TEST_BANK_ID);

            ProductType type = new ProductType();
            type.setName("Adjustment Test Type");
            type.setBankId(TEST_BANK_ID);
            productTypeRepository.save(type);

            Long p1Id = txHelper.createProductInDb("Product A", type.getId(), "RETAIL");
            Long p2Id = txHelper.createProductInDb("Product B", type.getId(), "RETAIL");

            PricingComponent feeComp = txHelper.createPricingComponentInDb("Standard Fee");
            txHelper.linkProductToPricingComponent(p1Id, feeComp.getId(), new BigDecimal("10.00"));
            txHelper.linkProductToPricingComponent(p2Id, feeComp.getId(), new BigDecimal("5.00"));

            // Create Bundle and apply a -2.00 Discount
            ProductBundle bundle = txHelper.createBundleInDb("Discounted Bundle");
            PricingComponent discountComp = txHelper.createPricingComponentInDb("Bundle Discount");
            txHelper.linkBundleToPricingComponent(bundle.getId(), discountComp.getId(), new BigDecimal("-2.00"));

            return Map.of("p1", p1Id, "p2", p2Id, "bundle", bundle.getId());
        });

        BundlePriceRequest request = new BundlePriceRequest();
        request.setProductBundleId(ids.get("bundle"));
        request.setCustomerSegment("RETAIL");
        request.setProducts(List.of(
                new BundlePriceRequest.ProductRequest(ids.get("p1"), BigDecimal.valueOf(1000)),
                new BundlePriceRequest.ProductRequest(ids.get("p2"), BigDecimal.valueOf(1000))
        ));

        // 2. ACT & ASSERT
        mockMvc.perform(post(BASE_URL + "/calculate/bundle")
                        .header("X-Bank-Id", TEST_BANK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossTotalAmount").value(15.00)) // 10.00 + 5.00
                .andExpect(jsonPath("$.netTotalAmount").value(13.00))   // 15.00 - 2.00
                .andExpect(jsonPath("$.bundleAdjustments[0].componentCode").value("Bundle Discount"))
                .andExpect(jsonPath("$.bundleAdjustments[0].amount").value(-2.00));
    }

    @Test
    @WithMockRole(roles = {PRICING_READER_ROLE})
    void calculateProductPrice_ShouldReturn400_WhenValidationFails() throws Exception {
        PricingRequest request = new PricingRequest();

        mockMvc.perform(post(BASE_URL + "/calculate/product")
                        .header("X-Bank-Id", TEST_BANK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void calculateProductPrice_ShouldReturn401_WhenUnauthenticated() throws Exception {
        PricingRequest request = new PricingRequest();

        mockMvc.perform(post(BASE_URL + "/calculate/product")
                        .header("X-Bank-Id", TEST_BANK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}