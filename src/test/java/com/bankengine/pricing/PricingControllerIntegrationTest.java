package com.bankengine.pricing;

import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.pricing.dto.BundlePriceRequest;
import com.bankengine.pricing.dto.ProductPriceRequest;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.ProductPricingLink;
import com.bankengine.pricing.repository.*;
import com.bankengine.pricing.service.ProductRuleBuilderService;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.bankengine.test.config.WithMockRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class PricingControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestTransactionHelper txHelper;
    @Autowired
    private ProductTypeRepository productTypeRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private PricingComponentRepository pricingComponentRepository;
    @Autowired
    private PricingTierRepository pricingTierRepository;
    @Autowired
    private ProductPricingLinkRepository productPricingLinkRepository;
    @Autowired
    private BundlePricingLinkRepository bundlePricingLinkRepository;
    @Autowired
    private ProductRuleBuilderService productRuleBuilderService;
    @Autowired
    private PriceValueRepository priceValueRepository;
    @Autowired
    private TierConditionRepository tierConditionRepository;

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
            txHelper.setupCommittedMetadata();
        });
        entityManager.clear();
    }

    @AfterEach
    void tearDown() {
        txHelper.doInTransaction(() -> {
            // 1. Delete links first (they point to products and components)
            productPricingLinkRepository.deleteAllInBatch();
            bundlePricingLinkRepository.deleteAllInBatch();

            // 2. Delete the lowest level children of Tiers
            priceValueRepository.deleteAllInBatch();
            tierConditionRepository.deleteAllInBatch();

            // 3. Now delete the Tiers
            pricingTierRepository.deleteAllInBatch();

            // 4. Delete the Components and Bundles/Products
            pricingComponentRepository.deleteAllInBatch();
            productRepository.deleteAllInBatch();

            // Optional: clear product types if you want a totally fresh slate
            productTypeRepository.deleteAllInBatch();
        });
    }

    @Test
    @WithMockRole(roles = {PRICING_READER_ROLE})
    void calculateProductPrice_ShouldReturn200_WhenRequestIsValid() throws Exception {
        Long productId = txHelper.doInTransaction(() -> {
            ProductType type = new ProductType();
            type.setName("Savings Type");
            type.setCode("ST1");
            type.setBankId(TEST_BANK_ID);
            Long typeId = productTypeRepository.save(type).getId();

            Long pId = txHelper.createProductInDb("Savings Account", typeId, "RETAIL");
            PricingComponent component = txHelper.createPricingComponentInDb("Monthly Fee");

            txHelper.linkProductToPricingComponent(pId, component.getId(), new BigDecimal("10.00"));
            return pId;
        });

        ProductPriceRequest request = new ProductPriceRequest();
        request.setProductId(productId);
        request.setCustomerSegment("RETAIL");
        request.setTransactionAmount(BigDecimal.valueOf(1000.0));
        request.setEffectiveDate(LocalDate.now());
        request.setCustomAttributes(Map.of("transactionAmount", new BigDecimal("1000")));

        mockMvc.perform(postWithCsrf(BASE_URL + "/calculate/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalChargeablePrice").exists())
                .andExpect(jsonPath("$.componentBreakdown[0].componentCode").value(startsWith("MONTHLY_FEE")));
    }

    @Test
    @WithMockRole(roles = {PRICING_READER_ROLE})
    void calculateBundlePrice_ShouldReturn200_WhenRequestIsValid() throws Exception {
        Map<String, Long> ids = txHelper.doInTransaction(() -> {
            ProductType type = new ProductType();
            type.setName("Bundle Type");
            type.setCode("BT1");
            type.setBankId(TEST_BANK_ID);
            Long typeId = productTypeRepository.save(type).getId();

            Long p1Id = txHelper.createProductInDb("Product 1", typeId, "RETAIL");
            Long p2Id = txHelper.createProductInDb("Product 2", typeId, "RETAIL");

            PricingComponent component = txHelper.createPricingComponentInDb("Standard Fee");
            txHelper.linkProductToPricingComponent(p1Id, component.getId(), new BigDecimal("10.00"));
            txHelper.linkProductToPricingComponent(p2Id, component.getId(), new BigDecimal("5.00"));

            ProductBundle bundle = txHelper.createBundleInDb("Super Salary Package", VersionableEntity.EntityStatus.ACTIVE);

            return Map.of("p1", p1Id, "p2", p2Id, "bundle", bundle.getId());
        });

        BundlePriceRequest request = new BundlePriceRequest();
        request.setProductBundleId(ids.get("bundle"));
        request.setCustomerSegment("RETAIL");

        BundlePriceRequest.BundleProductItem pr1 = new BundlePriceRequest.BundleProductItem(ids.get("p1"), BigDecimal.valueOf(1000));
        BundlePriceRequest.BundleProductItem pr2 = new BundlePriceRequest.BundleProductItem(ids.get("p2"), BigDecimal.valueOf(1000));

        request.setProducts(List.of(pr1, pr2));

        mockMvc.perform(postWithCsrf(BASE_URL + "/calculate/bundle")
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
        // 1. ARRANGE: Create products and 3 bundle-level adjustments
        Map<String, Long> ids = txHelper.doInTransaction(() -> {
            ProductType type = txHelper.getOrCreateProductType("Multi-Adjustment Type");
            Long p1Id = txHelper.createProductInDb("Product A", type.getId(), "RETAIL");
            Long p2Id = txHelper.createProductInDb("Product B", type.getId(), "RETAIL");

            PricingComponent productFee = txHelper.createPricingComponentInDb("Standard Fee");
            txHelper.linkProductToPricingComponent(p1Id, productFee.getId(), new BigDecimal("10.00"), PriceValue.ValueType.FEE_ABSOLUTE);
            txHelper.linkProductToPricingComponent(p2Id, productFee.getId(), new BigDecimal("5.00"), PriceValue.ValueType.FEE_ABSOLUTE);

            ProductBundle bundle = txHelper.createBundleInDb("Multi-Adjustment Bundle", VersionableEntity.EntityStatus.ACTIVE);

            // Adjustment 1: Fixed Bundle Discount (Negative)
            PricingComponent bundleDisc = txHelper.createPricingComponentInDb("Bundle Discount");
            txHelper.linkBundleToPricingComponent(bundle.getId(), bundleDisc.getId(), new BigDecimal("-2.00"), PriceValue.ValueType.DISCOUNT_ABSOLUTE);

            // Adjustment 2: Fixed Bundle Fee (Positive)
            PricingComponent bundleFee = txHelper.createPricingComponentInDb("Bundle Handling Fee");
            txHelper.linkBundleToPricingComponent(bundle.getId(), bundleFee.getId(), new BigDecimal("1.50"), PriceValue.ValueType.FEE_ABSOLUTE);

            // Note: The 3rd adjustment would typically come from Drools in this test environment
            // if your Mock rules engine is configured to return one.

            return Map.of("p1", p1Id, "p2", p2Id, "bundle", bundle.getId());
        });

        BundlePriceRequest request = new BundlePriceRequest();
        request.setProductBundleId(ids.get("bundle"));
        request.setCustomerSegment("RETAIL");
        request.setProducts(List.of(
                new BundlePriceRequest.BundleProductItem(ids.get("p1"), BigDecimal.valueOf(1000)),
                new BundlePriceRequest.BundleProductItem(ids.get("p2"), BigDecimal.valueOf(1000))
        ));

        // 2. ACT & ASSERT
        mockMvc.perform(postWithCsrf(BASE_URL + "/calculate/bundle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                /* Logic Check:
                   Products: 10.00 + 5.00 = 15.00
                   Bundle Fee: +1.50
                   Gross Total: 15.00 + 1.50 = 16.50
                   Bundle Discount: -2.00
                   Net Total: 16.50 - 2.00 = 14.50
                */
                .andExpect(jsonPath("$.grossTotalAmount").value(16.50))
                .andExpect(jsonPath("$.netTotalAmount").value(14.50))
                .andExpect(jsonPath("$.bundleAdjustments.length()").value(2))

                // Verify Adjustment Details
                .andExpect(jsonPath("$.bundleAdjustments[0].componentCode").value(startsWith("BUNDLE_DISCOUNT")))
                .andExpect(jsonPath("$.bundleAdjustments[0].calculatedAmount").value(-2.00))
                .andExpect(jsonPath("$.bundleAdjustments[0].valueType").value("DISCOUNT_ABSOLUTE"))
                .andExpect(jsonPath("$.bundleAdjustments[0].sourceType").value("FIXED_VALUE"))

                // Verify Second Adjustment (Fee)
                .andExpect(jsonPath("$.bundleAdjustments[1].componentCode").value(startsWith("BUNDLE_HANDLING_FEE")))
                .andExpect(jsonPath("$.bundleAdjustments[1].calculatedAmount").value(1.50))
                .andExpect(jsonPath("$.bundleAdjustments[1].valueType").value("FEE_ABSOLUTE"))
                .andExpect(jsonPath("$.bundleAdjustments[1].sourceType").value("FIXED_VALUE"));
    }

    @Test
    @WithMockRole(roles = {PRICING_READER_ROLE})
    void calculateProductPrice_ShouldHandlePercentageFeeAndDiscount() throws Exception {
        // 1. ARRANGE: Create a product with 1% Fee and 10% Discount
        Long productId = txHelper.doInTransaction(() -> {
            ProductType type = txHelper.getOrCreateProductType("Percentage Test Type");
            Long pId = txHelper.createProductInDb("High Value Account", type.getId(), "RETAIL");

            // Component 1: 1% Transaction Fee
            PricingComponent feeComp = txHelper.createPricingComponentInDb("Service Fee %");
            txHelper.linkProductToPricingComponent(pId, feeComp.getId(), new BigDecimal("1.00"), PriceValue.ValueType.FEE_PERCENTAGE);

            // Component 2: 10% Discount on that fee
            PricingComponent discComp = txHelper.createPricingComponentInDb("Loyalty Discount %");
            txHelper.linkProductToPricingComponent(pId, discComp.getId(), new BigDecimal("10.00"), PriceValue.ValueType.DISCOUNT_PERCENTAGE);

            return pId;
        });

        ProductPriceRequest request = new ProductPriceRequest();
        request.setProductId(productId);
        request.setCustomerSegment("RETAIL");
        request.setTransactionAmount(new BigDecimal("1000.00"));
        request.setEffectiveDate(LocalDate.now());

        // 2. ACT & ASSERT
        mockMvc.perform(postWithCsrf(BASE_URL + "/calculate/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                /* Math Check:
                   Gross Fee: 1000 * 0.01 = 10.00
                   Discount: 10.00 * 0.10 = 1.00
                   Final: 10.00 - 1.00 = 9.00
                */
                .andExpect(jsonPath("$.finalChargeablePrice").value(9.00))
                .andExpect(jsonPath("$.componentBreakdown.length()").value(2))

                // Verify Fee Detail
                .andExpect(jsonPath("$.componentBreakdown[0].componentCode").value(startsWith("SERVICE_FEE")))
                .andExpect(jsonPath("$.componentBreakdown[0].calculatedAmount").value(10.00))

                // Verify Discount Detail
                .andExpect(jsonPath("$.componentBreakdown[1].componentCode").value(startsWith("LOYALTY_DISCOUNT")))
                .andExpect(jsonPath("$.componentBreakdown[1].calculatedAmount").value(-1.00));
    }

    @Test
    @WithMockRole(roles = {PRICING_READER_ROLE})
    void calculateProductPrice_ShouldPopulateMatchedTierId_WhenUsingRules() throws Exception {
        String componentName = "Tiered Service Fee";
        String tierName = "Gold Segment Tier";

        Long productId = txHelper.doInTransaction(() -> {
            Long compId = txHelper.createLinkedTierAndValue(componentName, tierName);
            ProductType type = txHelper.getOrCreateProductType("Tiered Type");
            Long pId = txHelper.createProductInDb("Tiered Account", type.getId(), "RETAIL");

            ProductPricingLink link = txHelper.linkProductToPricingComponentReturn(pId, compId, null, null);
            link.setUseRulesEngine(true);
            productPricingLinkRepository.save(link);

            return pId;
        });

        // Ensure Drools picks up the new Tier condition (customerSegment == DEFAULT_SEGMENT)
        txHelper.doInTransaction(() -> {
            productRuleBuilderService.rebuildRules();
        });

        ProductPriceRequest request = new ProductPriceRequest();
        request.setProductId(productId);
        request.setCustomerSegment("DEFAULT_SEGMENT");
        request.setTransactionAmount(new BigDecimal("1000.00"));

        mockMvc.perform(postWithCsrf(BASE_URL + "/calculate/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.componentBreakdown.length()").value(1))
                .andExpect(jsonPath("$.componentBreakdown[0].componentCode").value(startsWith("TIERED_SERVICE_FEE")))
                .andExpect(jsonPath("$.componentBreakdown[0].matchedTierId").isNotEmpty())
                .andExpect(jsonPath("$.componentBreakdown[0].sourceType").value("RULES_ENGINE"));
    }

    @Test
    @WithMockRole(roles = {PRICING_READER_ROLE})
    void calculateProductPrice_ShouldPopulateTargetComponentCode_WhenDiscountIsTargeted() throws Exception {
        // 1. ARRANGE: Create a specific fee and a discount targeted at it
        Long productId = txHelper.doInTransaction(() -> {
            ProductType type = txHelper.getOrCreateProductType("Targeted Type");
            Long pId = txHelper.createProductInDb("Targeted Account", type.getId(), "RETAIL");

            // Fee: ATM Fee
            PricingComponent atmFee = txHelper.createPricingComponentInDb("ATM_FEE");
            atmFee.setCode("ATM_FEE_CODE");
            pricingComponentRepository.save(atmFee);
            txHelper.linkProductToPricingComponent(pId, atmFee.getId(), new BigDecimal("5.00"), PriceValue.ValueType.FEE_ABSOLUTE);

            // Discount: ATM Waiver (Fixed Value Link - Now supported by LEFT JOIN)
            PricingComponent disc = txHelper.createPricingComponentInDb("ATM_WAIVER");
            disc.setCode("ATM_WAIVER_CODE");
            pricingComponentRepository.save(disc);
            txHelper.linkProductToPricingComponent(pId, disc.getId(), new BigDecimal("100.00"), PriceValue.ValueType.DISCOUNT_PERCENTAGE);

            // Since the current helper doesn't support setting 'targetComponentCode', we update the link manually
            ProductPricingLink link = productPricingLinkRepository.findByProductId(pId).stream()
                    .filter(l -> l.getPricingComponent().getCode().equals("ATM_WAIVER_CODE"))
                    .findFirst().orElseThrow();

            link.setTargetComponentCode("ATM_FEE_CODE");
            productPricingLinkRepository.save(link);

            return pId;
        });

        txHelper.flushAndClear();

        ProductPriceRequest request = new ProductPriceRequest();
        request.setProductId(productId);
        request.setCustomerSegment("RETAIL");
        request.setTransactionAmount(BigDecimal.ZERO);
        request.setEffectiveDate(LocalDate.now());

        mockMvc.perform(postWithCsrf(BASE_URL + "/calculate/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.componentBreakdown.length()").value(2))
                .andExpect(jsonPath("$.componentBreakdown[1].targetComponentCode").value("ATM_FEE_CODE"))
                .andExpect(jsonPath("$.componentBreakdown[1].calculatedAmount").value(-5.00))
                .andExpect(jsonPath("$.finalChargeablePrice").value(0.00));
    }

    @Test
    @WithMockRole(roles = {PRICING_READER_ROLE})
    void calculateProductPrice_ShouldReturn400_WhenValidationFails() throws Exception {
        ProductPriceRequest request = new ProductPriceRequest();

        mockMvc.perform(postWithCsrf(BASE_URL + "/calculate/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void calculateProductPrice_ShouldReturn401_WhenUnauthenticated() throws Exception {
        ProductPriceRequest request = new ProductPriceRequest();

        mockMvc.perform(postWithCsrf(BASE_URL + "/calculate/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}