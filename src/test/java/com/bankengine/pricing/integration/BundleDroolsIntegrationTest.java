package com.bankengine.pricing.integration;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductBundleRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.pricing.TestTransactionHelper;
import com.bankengine.pricing.dto.BundlePriceRequest;
import com.bankengine.pricing.dto.BundlePriceResponse;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.model.BundlePricingLink;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.repository.BundlePricingLinkRepository;
import com.bankengine.pricing.repository.PriceValueRepository;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.repository.PricingTierRepository;
import com.bankengine.pricing.service.BundlePricingService;
import com.bankengine.pricing.service.PricingAttributeKeys;
import com.bankengine.pricing.service.ProductPricingService;
import com.bankengine.rules.service.KieContainerReloadService;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.bankengine.test.config.WithMockRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.bankengine.common.model.VersionableEntity.EntityStatus.ACTIVE;
import static com.bankengine.common.util.CodeGeneratorUtil.generateValidCode;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WithMockRole(roles = {"PRICING_ADMIN"})
public class BundleDroolsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductBundleRepository bundleRepository;
    @Autowired
    private BundlePricingLinkRepository bundlePricingLinkRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductTypeRepository productTypeRepository;
    @Autowired
    private PricingComponentRepository pricingComponentRepository;
    @Autowired
    private PricingTierRepository pricingTierRepository;
    @Autowired
    private PriceValueRepository priceValueRepository;

    @Autowired
    private BundlePricingService bundlePricingService;
    @Autowired
    private TestTransactionHelper txHelper;
    @Autowired
    private KieContainerReloadService kieContainerReloadService;

    @MockBean
    private ProductPricingService productPricingService;

    private Long bundleId;
    private Long productId;

    @BeforeEach
    void setupBaseCatalog() {
        TenantContextHolder.setBankId(TEST_BANK_ID);
        txHelper.doInTransaction(() -> {
            cleanupData();
            txHelper.ensureProductCategoryExists(TEST_BANK_ID, "RETAIL");
            ProductType type = productTypeRepository.save(ProductType.builder()
                    .name("SAVINGS_TYPE").code("SAV_TYPE").bankId(TEST_BANK_ID).build());

            Product product = productRepository.save(Product.builder()
                    .name("Integration Product").code("PROD-INT-01").version(1).category("RETAIL")
                    .productType(type).bankId(TEST_BANK_ID).build());
            this.productId = product.getId();

            ProductBundle bundle = bundleRepository.save(ProductBundle.builder()
                    .name("Drools Test Bundle").code("BNDL-DR-01").version(1)
                    .bankId(TEST_BANK_ID).status(ACTIVE)
                    .targetCustomerSegments("RETAIL,CORPORATE").build());
            bundleId = bundle.getId();
        });
    }

    @AfterEach
    void tearDown() {
        txHelper.doInTransaction(this::cleanupData);
    }

    private void cleanupData() {
        bundlePricingLinkRepository.deleteAllInBatch();
        priceValueRepository.deleteAllInBatch();
        pricingTierRepository.deleteAllInBatch();
        bundleRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        productTypeRepository.deleteAllInBatch();
        pricingComponentRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("Bundle Drools: Should apply rule-based flat discount")
    void shouldApplyRuleBasedFlatDiscount_WhenHighValueCriteriaMet() {
        // 1. Setup specific rule for this test
        final String COMP_CODE = "BNDL-DISC-01";
        setupBundlePricingRule(COMP_CODE, "HighValueDiscount", new BigDecimal("100.00"), PriceValue.ValueType.DISCOUNT_ABSOLUTE);
        reloadRules();

        BundlePriceRequest request = BundlePriceRequest.builder()
                .productBundleId(bundleId)
                .customAttributes(Map.of(
                        PricingAttributeKeys.EFFECTIVE_DATE, LocalDate.now(),
                        PricingAttributeKeys.CUSTOMER_SEGMENT, "RETAIL"))
                .products(List.of(new BundlePriceRequest.BundleProductItem(productId, new BigDecimal("1000.00"))))
                .build();

        // Stub product pricing at $500
        when(productPricingService.getProductPricing(any()))
                .thenReturn(ProductPricingCalculationResult.builder()
                        .finalChargeablePrice(new BigDecimal("500.00"))
                        .componentBreakdown(new ArrayList<>())
                        .build());

        BundlePriceResponse response = bundlePricingService.calculateTotalBundlePrice(request);

        var discountOpt = response.getBundleAdjustments().stream()
                .filter(adj -> adj.getComponentCode().equals(COMP_CODE))
                .findFirst();

        System.out.println("ZZZZZ response = " + response);
        assertTrue(discountOpt.isPresent(), "The bundle rule '" + COMP_CODE + "' should have fired.");
        BigDecimal impactAmount = discountOpt.get().getCalculatedAmount();

        // Logical check: Discounts must be negative monetary impacts
        assertNotNull(impactAmount);
        assertTrue(impactAmount.compareTo(BigDecimal.ZERO) < 0, "Discounts must result in negative adjustments.");
        assertEquals(new BigDecimal("-100.00"), impactAmount);
    }

    @Test
    @DisplayName("Bundle Drools: Should accumulate multiple discounts (Loyalty + Bundle Level)")
    void shouldAccumulateMultipleDiscounts_WhenLoyaltyAndBundleRulesFire() {
        final BigDecimal productFee = new BigDecimal("500.00");

        // 1. Setup Two Rules: One for Loyalty ($50) and one for Bundle ($100)
        setupBundlePricingRule("LOYAL-01", "LOYALTY_DISCOUNT", new BigDecimal("50.00"), PriceValue.ValueType.DISCOUNT_ABSOLUTE);
        setupBundlePricingRule("BNDL-01", "BUNDLE_DISCOUNT", new BigDecimal("100.00"), PriceValue.ValueType.DISCOUNT_ABSOLUTE);
        reloadRules();

        when(productPricingService.getProductPricing(any()))
                .thenReturn(ProductPricingCalculationResult.builder()
                        .finalChargeablePrice(productFee).componentBreakdown(new ArrayList<>()).build());

        BundlePriceRequest request = BundlePriceRequest.builder()
                .productBundleId(bundleId)
                .customAttributes(Map.of(PricingAttributeKeys.CUSTOMER_SEGMENT, "RETAIL"))
                .products(List.of(new BundlePriceRequest.BundleProductItem(productId, productFee)))
                .build();

        BundlePriceResponse response = bundlePricingService.calculateTotalBundlePrice(request);
        System.out.println("YYYY response = " + response);

        // Net amount should be 500 - 50 - 100 = 350
        BigDecimal expectedNet = new BigDecimal("350.00");

        boolean hasLoyalty = response.getBundleAdjustments().stream()
                .anyMatch(a -> a.getComponentCode().equals("LOYAL-01"));

        assertTrue(hasLoyalty, "LOYAL-01 should be present in the breakdown.");
        assertEquals(0, expectedNet.compareTo(response.getNetTotalAmount()),
                "Net amount " + response.getNetTotalAmount() + " should reflect both deductions from " + productFee);
    }

    @Test
    @DisplayName("Safety: Should return $0 impact if discount target exceeds pool")
    void shouldHandleZeroImpact_WhenPoolIsEmpty() {
        // Setup a $100 discount
        setupBundlePricingRule("BNDL-DISC-01", "HighValueDiscount", new BigDecimal("100.00"), PriceValue.ValueType.DISCOUNT_ABSOLUTE);
        reloadRules();

        BundlePriceRequest request = BundlePriceRequest.builder()
                .productBundleId(bundleId)
                .products(List.of(new BundlePriceRequest.BundleProductItem(productId, BigDecimal.ZERO)))
                .build();

        // Stub product price at $0
        when(productPricingService.getProductPricing(any()))
                .thenReturn(ProductPricingCalculationResult.builder()
                        .finalChargeablePrice(BigDecimal.ZERO)
                        .componentBreakdown(new ArrayList<>()).build());

        var response = bundlePricingService.calculateTotalBundlePrice(request);

        System.out.println("XXX response = " + response);
        var discount = response.getBundleAdjustments().stream()
                .filter(a -> a.getComponentCode().equals("BNDL-DISC-01")).findFirst().orElseThrow();

        // Aggregator caps discount at the pool size (0.00)
        assertEquals(0, BigDecimal.ZERO.compareTo(discount.getCalculatedAmount()), "Discount should be capped at 0");
    }

    /**
     * Helper to dynamically create a Pricing Rule (Fact) during test execution.
     */
    private void setupBundlePricingRule(String code, String name, BigDecimal value, PriceValue.ValueType type) {
        txHelper.doInTransaction(() -> {
            PricingComponent comp = pricingComponentRepository.save(PricingComponent.builder()
                    .name(name).code(code).version(1)
                    .type(PricingComponent.ComponentType.DISCOUNT).bankId(TEST_BANK_ID).status(ACTIVE).build());

            PricingTier tier = pricingTierRepository.save(PricingTier.builder()
                    .pricingComponent(comp).name(name + " Tier").code(generateValidCode(name))
                    .minThreshold(BigDecimal.ZERO).bankId(TEST_BANK_ID).build());

            priceValueRepository.save(PriceValue.builder()
                    .pricingTier(tier).rawValue(value).valueType(type).bankId(TEST_BANK_ID).build());

            bundlePricingLinkRepository.save(BundlePricingLink.builder()
                    .productBundle(bundleRepository.getReferenceById(bundleId))
                    .useRulesEngine(true).pricingComponent(comp).bankId(TEST_BANK_ID)
                    .effectiveDate(LocalDate.now().minusDays(5))
                    .expiryDate(LocalDate.now().plusDays(5)).build());
        });
    }

    private void reloadRules() {
        try {
            TenantContextHolder.setSystemMode(true);
            TenantContextHolder.setBankId(TEST_BANK_ID);
            kieContainerReloadService.reloadKieContainer(TEST_BANK_ID);
        } finally {
            TenantContextHolder.setSystemMode(false);
            TenantContextHolder.setBankId(TEST_BANK_ID);
        }
    }
}