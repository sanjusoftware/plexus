package com.bankengine.pricing;

import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.model.ProductCategory;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductCategoryRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.pricing.dto.BundlePriceRequest;
import com.bankengine.pricing.dto.ProductPriceRequest;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.model.*;
import com.bankengine.pricing.repository.*;
import com.bankengine.pricing.service.PricingAttributeKeys;
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
import static org.junit.jupiter.api.Assertions.*;
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
    private ProductCategoryRepository productCategoryRepository;
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
            productCategoryRepository.findByBankIdAndCode(TEST_BANK_ID, "RETAIL")
                    .orElseGet(() -> {
                        ProductCategory category = new ProductCategory();
                        category.setCode("RETAIL");
                        category.setName("Retail");
                        return productCategoryRepository.save(category);
                    });
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
            productCategoryRepository.deleteAllInBatch();

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
        request.setCustomAttributes(Map.of(
                PricingAttributeKeys.CUSTOMER_SEGMENT, "RETAIL",
                PricingAttributeKeys.TRANSACTION_AMOUNT, BigDecimal.valueOf(1000.0),
                PricingAttributeKeys.EFFECTIVE_DATE, LocalDate.now()));

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
        request.setCustomAttributes(Map.of(PricingAttributeKeys.CUSTOMER_SEGMENT, "RETAIL"));

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
        request.setCustomAttributes(Map.of(PricingAttributeKeys.CUSTOMER_SEGMENT, "RETAIL"));
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
        request.setCustomAttributes(Map.of(
                PricingAttributeKeys.CUSTOMER_SEGMENT, "RETAIL",
                PricingAttributeKeys.TRANSACTION_AMOUNT, new BigDecimal("1000.00"),
                PricingAttributeKeys.EFFECTIVE_DATE, LocalDate.now()));

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
        request.setCustomAttributes(Map.of(
                PricingAttributeKeys.CUSTOMER_SEGMENT, "DEFAULT_SEGMENT",
                PricingAttributeKeys.TRANSACTION_AMOUNT, new BigDecimal("1000.00"),
                PricingAttributeKeys.EFFECTIVE_DATE, LocalDate.now()));

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
        request.setCustomAttributes(Map.of(
                PricingAttributeKeys.CUSTOMER_SEGMENT, "RETAIL",
                PricingAttributeKeys.TRANSACTION_AMOUNT, BigDecimal.ZERO,
                PricingAttributeKeys.EFFECTIVE_DATE, LocalDate.now()));

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
    void calculateProductPrice_ShouldLockAdvancedRuledMixScenarios_EndToEnd() throws Exception {
        Long productId = seedAdvancedRuledMixProduct();

        ProductPricingCalculationResult salaryScenario = performProductCalculation(ProductPriceRequest.builder()
                .productId(productId)
                .enrollmentDate(LocalDate.of(2026, 4, 13))
                .customAttributes(Map.of(
                        PricingAttributeKeys.CUSTOMER_SEGMENT, "RETAIL",
                        PricingAttributeKeys.EFFECTIVE_DATE, LocalDate.of(2026, 4, 13),
                        "ENROLLMENT_DATE", LocalDate.of(2026, 4, 13),
                        PricingAttributeKeys.GROSS_TOTAL_AMOUNT, BigDecimal.ZERO,
                        "IS_SALARY_ACCOUNT", true,
                        "LOYALTY_SCORE", new BigDecimal("90"),
                        PricingAttributeKeys.TRANSACTION_AMOUNT, new BigDecimal("60000")
                ))
                .build());

        assertEquals(new BigDecimal("1120.00"), salaryScenario.getFinalChargeablePrice());
        ProductPricingCalculationResult.PriceComponentDetail salaryDiscount = findDetail(salaryScenario, "ADV_SALARY_BASE_DISCOUNT");
        assertNotNull(salaryDiscount);
        assertEquals("ADV_BASE_FEE", salaryDiscount.getTargetComponentCode());
        assertEquals(new BigDecimal("-50.00"), salaryDiscount.getCalculatedAmount());

        ProductPricingCalculationResult thresholdScenario = performProductCalculation(ProductPriceRequest.builder()
                .productId(productId)
                .enrollmentDate(LocalDate.of(2026, 4, 13))
                .customAttributes(Map.of(
                        PricingAttributeKeys.CUSTOMER_SEGMENT, "RETAIL",
                        PricingAttributeKeys.EFFECTIVE_DATE, LocalDate.of(2026, 4, 13),
                        "ENROLLMENT_DATE", LocalDate.of(2026, 4, 13),
                        PricingAttributeKeys.GROSS_TOTAL_AMOUNT, BigDecimal.ZERO,
                        "IS_SALARY_ACCOUNT", false,
                        "LOYALTY_SCORE", new BigDecimal("20"),
                        PricingAttributeKeys.TRANSACTION_AMOUNT, new BigDecimal("50000")
                ))
                .build());

        assertEquals(new BigDecimal("1100.00"), thresholdScenario.getFinalChargeablePrice());
        ProductPricingCalculationResult.PriceComponentDetail txSurcharge = findDetail(thresholdScenario, "ADV_TX_SURCHARGE_RULED");
        assertNotNull(txSurcharge);
        assertEquals("TX_HIGH", txSurcharge.getMatchedTierCode());
        assertEquals(new BigDecimal("1000.00"), txSurcharge.getCalculatedAmount());
    }

    @Test
    @WithMockRole(roles = {PRICING_READER_ROLE})
    void calculateProductPrice_ShouldHonorConditionBasedBoundaryAtExactThreshold_EndToEnd() throws Exception {
        Long productId = seedAdvancedRuledMixProductUsingConditionThresholds();

        ProductPricingCalculationResult thresholdScenario = performProductCalculation(ProductPriceRequest.builder()
                .productId(productId)
                .enrollmentDate(LocalDate.of(2026, 4, 13))
                .customAttributes(Map.of(
                        PricingAttributeKeys.CUSTOMER_SEGMENT, "RETAIL",
                        PricingAttributeKeys.EFFECTIVE_DATE, LocalDate.of(2026, 4, 13),
                        "ENROLLMENT_DATE", LocalDate.of(2026, 4, 13),
                        PricingAttributeKeys.GROSS_TOTAL_AMOUNT, BigDecimal.ZERO,
                        "IS_SALARY_ACCOUNT", false,
                        "LOYALTY_SCORE", new BigDecimal("20"),
                        PricingAttributeKeys.TRANSACTION_AMOUNT, new BigDecimal("50000")
                ))
                .build());

        assertEquals(new BigDecimal("1100.00"), thresholdScenario.getFinalChargeablePrice());
        ProductPricingCalculationResult.PriceComponentDetail txSurcharge = findDetail(thresholdScenario, "ADV_TX_SURCHARGE_RULED");
        assertNotNull(txSurcharge);
        assertEquals("TX_HIGH", txSurcharge.getMatchedTierCode());
        assertEquals(new BigDecimal("1000.00"), txSurcharge.getCalculatedAmount());
    }

    @Test
    @WithMockRole(roles = {PRICING_READER_ROLE})
    void calculateProductPrice_ShouldLockAdvancedBreachProrataPayloads_EndToEnd() throws Exception {
        Long productId = seedAdvancedBreachProrataProduct();

        ProductPricingCalculationResult breachScenario = performProductCalculation(ProductPriceRequest.builder()
                .productId(productId)
                .enrollmentDate(LocalDate.of(2026, 4, 13))
                .customAttributes(Map.of(
                        PricingAttributeKeys.CUSTOMER_SEGMENT, "RETAIL",
                        PricingAttributeKeys.EFFECTIVE_DATE, LocalDate.of(2026, 4, 13),
                        "ENROLLMENT_DATE", LocalDate.of(2026, 3, 15),
                        PricingAttributeKeys.GROSS_TOTAL_AMOUNT, BigDecimal.ZERO,
                        "IS_SALARY_ACCOUNT", false,
                        "LOYALTY_SCORE", BigDecimal.ZERO,
                        PricingAttributeKeys.TRANSACTION_AMOUNT, new BigDecimal("12000")
                ))
                .build());

        assertEquals(new BigDecimal("618.00"), breachScenario.getFinalChargeablePrice());
        ProductPricingCalculationResult.PriceComponentDetail breachFee = findDetail(breachScenario, "ADV_BREACH_FEE");
        assertNotNull(breachFee);
        assertEquals("BREACH_FULL", breachFee.getMatchedTierCode());
        assertEquals(new BigDecimal("600.00"), breachFee.getCalculatedAmount());

        ProductPricingCalculationResult.PriceComponentDetail platformFee = findDetail(breachScenario, "ADV_PLATFORM_FEE_PRORATA");
        assertNotNull(platformFee);
        assertEquals(new BigDecimal("18.00"), platformFee.getCalculatedAmount());

        ProductPricingCalculationResult belowThresholdScenario = performProductCalculation(ProductPriceRequest.builder()
                .productId(productId)
                .enrollmentDate(LocalDate.of(2026, 4, 13))
                .customAttributes(Map.of(
                        PricingAttributeKeys.CUSTOMER_SEGMENT, "RETAIL",
                        PricingAttributeKeys.EFFECTIVE_DATE, LocalDate.of(2026, 4, 13),
                        "ENROLLMENT_DATE", LocalDate.of(2026, 3, 15),
                        PricingAttributeKeys.GROSS_TOTAL_AMOUNT, BigDecimal.ZERO,
                        "IS_SALARY_ACCOUNT", false,
                        "LOYALTY_SCORE", BigDecimal.ZERO,
                        PricingAttributeKeys.TRANSACTION_AMOUNT, new BigDecimal("9000")
                ))
                .build());

        assertEquals(new BigDecimal("18.00"), belowThresholdScenario.getFinalChargeablePrice());
        assertNull(findDetail(belowThresholdScenario, "ADV_BREACH_FEE"));
        ProductPricingCalculationResult.PriceComponentDetail belowThresholdPlatformFee = findDetail(belowThresholdScenario, "ADV_PLATFORM_FEE_PRORATA");
        assertNotNull(belowThresholdPlatformFee);
        assertEquals(new BigDecimal("18.00"), belowThresholdPlatformFee.getCalculatedAmount());
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

                    private Long seedAdvancedRuledMixProduct() {
                        return txHelper.doInTransaction(() -> {
                            txHelper.createAndSaveMetadata(PricingAttributeKeys.EFFECTIVE_DATE, "DATE");
                            txHelper.createAndSaveMetadata("LOYALTY_SCORE", "DECIMAL");
                            txHelper.createAndSaveMetadata("IS_SALARY_ACCOUNT", "BOOLEAN");

                            ProductType type = txHelper.getOrCreateProductType("ADVANCED_RULED_TYPE");
                            Product product = txHelper.getOrCreateProduct("ADV_PRODUCT_RULED_MIX", type, "RETAIL");

                            PricingComponent baseFee = createPricingComponent("Advanced Base Fee", "ADV_BASE_FEE", PricingComponent.ComponentType.FEE, false);
                            PricingComponent txSurcharge = createPricingComponent("Advanced Transaction Surcharge", "ADV_TX_SURCHARGE_RULED", PricingComponent.ComponentType.FEE, false);
                            PricingComponent loyaltyDiscount = createPricingComponent("Advanced Loyalty Discount", "ADV_LOYALTY_DISCOUNT", PricingComponent.ComponentType.DISCOUNT, false);
                            PricingComponent salaryDiscount = createPricingComponent("Advanced Salary Discount", "ADV_SALARY_BASE_DISCOUNT", PricingComponent.ComponentType.DISCOUNT, false);

                            addTier(txSurcharge, "TX High", "TX_HIGH", 100, new BigDecimal("50000"), null, false, new BigDecimal("2"), PriceValue.ValueType.FEE_PERCENTAGE, List.of());
                            addTier(txSurcharge, "TX Mid", "TX_MID", 50, new BigDecimal("20000"), null, false, new BigDecimal("1"), PriceValue.ValueType.FEE_PERCENTAGE, List.of());
                            addTier(loyaltyDiscount, "Loyalty High", "LOYALTY_HIGH", 100, null, null, false, new BigDecimal("10"), PriceValue.ValueType.DISCOUNT_PERCENTAGE,
                                    List.of(condition("LOYALTY_SCORE", TierCondition.Operator.GE, "80")));
                            addTier(salaryDiscount, "Salary True", "SALARY_TRUE", 100, null, null, false, new BigDecimal("50"), PriceValue.ValueType.DISCOUNT_PERCENTAGE,
                                    List.of(condition("IS_SALARY_ACCOUNT", TierCondition.Operator.EQ, "true")));

                            linkProduct(product, baseFee, false, new BigDecimal("100"), PriceValue.ValueType.FEE_ABSOLUTE, null);
                            linkProduct(product, txSurcharge, true, null, null, null);
                            linkProduct(product, loyaltyDiscount, true, null, null, null);
                            linkProduct(product, salaryDiscount, true, null, null, "ADV_BASE_FEE");

                            txHelper.flushAndClear();
                            productRuleBuilderService.rebuildRules();
                            return product.getId();
                        });
                    }

                    private Long seedAdvancedBreachProrataProduct() {
                        return txHelper.doInTransaction(() -> {
                            txHelper.createAndSaveMetadata(PricingAttributeKeys.EFFECTIVE_DATE, "DATE");

                            ProductType type = txHelper.getOrCreateProductType("ADVANCED_BREACH_TYPE");
                            Product product = txHelper.getOrCreateProduct("ADV_PRODUCT_BREACH_PRORATA", type, "RETAIL");

                            PricingComponent breachFee = createPricingComponent("Advanced Breach Fee", "ADV_BREACH_FEE", PricingComponent.ComponentType.FEE, false);
                            PricingComponent platformFee = createPricingComponent("Advanced Platform Fee", "ADV_PLATFORM_FEE_PRORATA", PricingComponent.ComponentType.FEE, true);

                            addTier(breachFee, "Full Breach", "BREACH_FULL", 100, new BigDecimal("10000"), null, true, new BigDecimal("5"), PriceValue.ValueType.FEE_PERCENTAGE, List.of());

                            linkProduct(product, breachFee, true, null, null, null);
                            linkProduct(product, platformFee, false, new BigDecimal("30"), PriceValue.ValueType.FEE_ABSOLUTE, null);

                            txHelper.flushAndClear();
                            productRuleBuilderService.rebuildRules();
                            return product.getId();
                        });
                    }

                    private Long seedAdvancedRuledMixProductUsingConditionThresholds() {
                        return txHelper.doInTransaction(() -> {
                            txHelper.createAndSaveMetadata(PricingAttributeKeys.EFFECTIVE_DATE, "DATE");
                            txHelper.createAndSaveMetadata("LOYALTY_SCORE", "DECIMAL");
                            txHelper.createAndSaveMetadata("IS_SALARY_ACCOUNT", "BOOLEAN");

                            ProductType type = txHelper.getOrCreateProductType("ADVANCED_RULED_TYPE_CONDITION");
                            Product product = txHelper.getOrCreateProduct("ADV_PRODUCT_RULED_MIX_CONDITION", type, "RETAIL");

                            PricingComponent baseFee = createPricingComponent("Advanced Base Fee (Condition)", "ADV_BASE_FEE", PricingComponent.ComponentType.FEE, false);
                            PricingComponent txSurcharge = createPricingComponent("Advanced Transaction Surcharge (Condition)", "ADV_TX_SURCHARGE_RULED", PricingComponent.ComponentType.FEE, false);

                            addTier(txSurcharge, "TX High", "TX_HIGH", 100, null, null, false, new BigDecimal("2"), PriceValue.ValueType.FEE_PERCENTAGE,
                                    List.of(condition(PricingAttributeKeys.TRANSACTION_AMOUNT, TierCondition.Operator.GE, "50000")));
                            addTier(txSurcharge, "TX Mid", "TX_MID", 50, null, null, false, new BigDecimal("1"), PriceValue.ValueType.FEE_PERCENTAGE,
                                    List.of(condition(PricingAttributeKeys.TRANSACTION_AMOUNT, TierCondition.Operator.GE, "20000")));

                            linkProduct(product, baseFee, false, new BigDecimal("100"), PriceValue.ValueType.FEE_ABSOLUTE, null);
                            linkProduct(product, txSurcharge, true, null, null, null);

                            txHelper.flushAndClear();
                            productRuleBuilderService.rebuildRules();
                            return product.getId();
                        });
                    }

                    private PricingComponent createPricingComponent(String name, String code, PricingComponent.ComponentType type, boolean proRataApplicable) {
                        PricingComponent component = new PricingComponent();
                        component.setName(name);
                        component.setCode(code);
                        component.setVersion(1);
                        component.setStatus(VersionableEntity.EntityStatus.DRAFT);
                        component.setType(type);
                        component.setProRataApplicable(proRataApplicable);
                        return pricingComponentRepository.save(component);
                    }

                    private void addTier(PricingComponent component,
                                         String tierName,
                                         String tierCode,
                                         int priority,
                                         BigDecimal minThreshold,
                                         BigDecimal maxThreshold,
                                         boolean applyChargeOnFullBreach,
                                         BigDecimal rawValue,
                                         PriceValue.ValueType valueType,
                                         List<TierCondition> conditions) {
                        PricingTier tier = new PricingTier();
                        tier.setPricingComponent(component);
                        tier.setName(tierName);
                        tier.setCode(tierCode);
                        tier.setPriority(priority);
                        tier.setMinThreshold(minThreshold);
                        tier.setMaxThreshold(maxThreshold);
                        tier.setApplyChargeOnFullBreach(applyChargeOnFullBreach);
                        PricingTier savedTier = pricingTierRepository.save(tier);

                        PriceValue value = new PriceValue();
                        value.setPricingTier(savedTier);
                        value.setRawValue(rawValue);
                        value.setValueType(valueType);
                        priceValueRepository.save(value);

                        for (TierCondition template : conditions) {
                            TierCondition condition = new TierCondition();
                            condition.setPricingTier(savedTier);
                            condition.setAttributeName(template.getAttributeName());
                            condition.setOperator(template.getOperator());
                            condition.setAttributeValue(template.getAttributeValue());
                            condition.setConnector(template.getConnector());
                            tierConditionRepository.save(condition);
                        }
                    }

                    private TierCondition condition(String attributeName, TierCondition.Operator operator, String attributeValue) {
                        TierCondition condition = new TierCondition();
                        condition.setAttributeName(attributeName);
                        condition.setOperator(operator);
                        condition.setAttributeValue(attributeValue);
                        return condition;
                    }

                    private void linkProduct(Product product,
                                             PricingComponent component,
                                             boolean useRulesEngine,
                                             BigDecimal fixedValue,
                                             PriceValue.ValueType fixedValueType,
                                             String targetComponentCode) {
                        ProductPricingLink link = new ProductPricingLink();
                        link.setProduct(product);
                        link.setPricingComponent(component);
                        link.setUseRulesEngine(useRulesEngine);
                        link.setFixedValue(fixedValue);
                        link.setFixedValueType(fixedValueType);
                        link.setTargetComponentCode(targetComponentCode);
                        link.setEffectiveDate(LocalDate.of(2026, 1, 1));
                        link.setExpiryDate(LocalDate.of(2030, 12, 31));
                        productPricingLinkRepository.save(link);
                    }

                    private ProductPricingCalculationResult performProductCalculation(ProductPriceRequest request) throws Exception {
                        String response = mockMvc.perform(postWithCsrf(BASE_URL + "/calculate/product")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                        return objectMapper.readValue(response, ProductPricingCalculationResult.class);
                    }

                    private ProductPricingCalculationResult.PriceComponentDetail findDetail(ProductPricingCalculationResult result, String componentCode) {
                        return result.getComponentBreakdown().stream()
                                .filter(detail -> componentCode.equals(detail.getComponentCode()))
                                .findFirst()
                                .orElse(null);
                    }
}