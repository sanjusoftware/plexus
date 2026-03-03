package com.bankengine.pricing.integration;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.data.seeding.CoreMetadataSeeder;
import com.bankengine.pricing.TestTransactionHelper;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.dto.ProductPricingCalculationResult.PriceComponentDetail;
import com.bankengine.pricing.dto.ProductPricingRequest;
import com.bankengine.pricing.model.*;
import com.bankengine.pricing.model.PriceValue.ValueType;
import com.bankengine.pricing.model.TierCondition.Operator;
import com.bankengine.pricing.repository.*;
import com.bankengine.pricing.service.PricingComponentService;
import com.bankengine.pricing.service.ProductPricingService;
import com.bankengine.rules.service.KieContainerReloadService;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.bankengine.test.config.WithMockRole;
import com.bankengine.web.exception.NotFoundException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockRole(roles = {"PRICING_ADMIN"})
public class DroolsIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_COMPONENT_NAME = "AnnualFeeComponent";
    private static final String TEST_SEGMENT = "RETAIL";
    private static final BigDecimal TEST_AMOUNT = new BigDecimal("1000.00");
    private static final BigDecimal EXPECTED_PRICE_INITIAL = new BigDecimal("10.00");

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductTypeRepository productTypeRepository;
    @Autowired
    private PricingTierRepository pricingTierRepository;
    @Autowired
    private PriceValueRepository priceValueRepository;
    @Autowired
    private TierConditionRepository tierConditionRepository;
    @Autowired
    private PricingComponentRepository pricingComponentRepository;
    @Autowired
    private ProductPricingLinkRepository productPricingLinkRepository;
    @Autowired
    private ProductPricingService productPricingService;
    @Autowired
    private PricingComponentService pricingComponentService;
    @Autowired
    private KieContainerReloadService kieContainerReloadService;
    @Autowired
    private CoreMetadataSeeder coreMetadataSeeder;
    @Autowired
    private TestTransactionHelper txHelper;
    @Autowired
    private MockMvc mockMvc;

    private Product persistedProduct;
    private Long existingTierId;

    @BeforeAll
    static void setupRoles(@Autowired TestTransactionHelper txHelperStatic) {
        seedBaseRoles(txHelperStatic, Map.of(
                "PRICING_ADMIN", Set.of(
                        "pricing:calculation:read",
                        "pricing:component:write",
                        "pricing:component:delete",
                        "rules:management:reload"
                )
        ));
    }

    @BeforeEach
    void setup() {
        TenantContextHolder.setBankId(TEST_BANK_ID);


        txHelper.doInTransaction(() -> {
            cleanupData();
            coreMetadataSeeder.seedCorePricingInputMetadata(TEST_BANK_ID);
            ProductType type = productTypeRepository.save(ProductType.builder()
                    .name("LOAN_TYPE").bankId(TEST_BANK_ID).build());

            persistedProduct = productRepository.save(Product.builder()
                    .name("Test Loan").code("TEST-LOAN-001")
                    .productType(type).category("RETAIL").bankId(TEST_BANK_ID).build());

            PricingComponent component = pricingComponentRepository.save(PricingComponent.builder()
                    .name(TEST_COMPONENT_NAME).code("FEE-001")
                    .type(PricingComponent.ComponentType.FEE).bankId(TEST_BANK_ID).build());

            PricingTier tier = PricingTier.builder()
                    .pricingComponent(component).name("Base Tier")
                    .minThreshold(BigDecimal.ZERO).bankId(TEST_BANK_ID).build();

            tier.getConditions().add(TierCondition.builder()
                    .pricingTier(tier).attributeName("customerSegment").operator(Operator.EQ)
                    .attributeValue(TEST_SEGMENT).connector(TierCondition.LogicalConnector.AND)
                    .bankId(TEST_BANK_ID).build());

            tier.getPriceValues().add(PriceValue.builder()
                    .pricingTier(tier).rawValue(EXPECTED_PRICE_INITIAL)
                    .valueType(ValueType.FEE_ABSOLUTE).bankId(TEST_BANK_ID).build());

            pricingTierRepository.save(tier);
            this.existingTierId = tier.getId();

            productPricingLinkRepository.save(ProductPricingLink.builder()
                    .product(persistedProduct).pricingComponent(component)
                    .bankId(TEST_BANK_ID).effectiveDate(LocalDate.now().minusDays(5))
                    .useRulesEngine(true).build());
        });

        reloadRules();
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.setBankId(TEST_BANK_ID);
        txHelper.doInTransaction(this::cleanupData);
        TenantContextHolder.clear();
    }

    private void cleanupData() {
        productPricingLinkRepository.deleteAllInBatch();
        priceValueRepository.deleteAllInBatch();
        tierConditionRepository.deleteAllInBatch();
        pricingTierRepository.deleteAllInBatch();
        pricingComponentRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        productTypeRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("Success - Standard rule execution returns expected price")
    void testStandardRuleExecution_Success() {
        ProductPricingRequest request = ProductPricingRequest.builder()
                .productId(this.persistedProduct.getId())
                .customerSegment(TEST_SEGMENT).transactionAmount(TEST_AMOUNT).build();

        ProductPricingCalculationResult result = productPricingService.getProductPricing(request);
        assertEquals(EXPECTED_PRICE_INITIAL, result.getComponentBreakdown().getFirst().getRawValue());
    }

    @Test
    @DisplayName("Temporal - Link with future effective date should not activate its component rules today")
    void testFutureDatedLink_DoesNotActivateRulesToday() {
        final Long[] futureComponentTierId = new Long[1];

        txHelper.doInTransaction(() -> {
            // 1. Create a NEW component that will be linked in the future
            PricingComponent futureLinkComponent = pricingComponentRepository.save(PricingComponent.builder()
                    .name("FutureLinkedComponent").code("FEE-FUTURE-LINK")
                    .type(PricingComponent.ComponentType.FEE).bankId(TEST_BANK_ID).build());

            PricingTier tierInsideFutureLink = PricingTier.builder()
                    .pricingComponent(futureLinkComponent).name("Tier inside future link")
                    .minThreshold(BigDecimal.ZERO).bankId(TEST_BANK_ID).build();

            tierInsideFutureLink.getPriceValues().add(PriceValue.builder()
                    .pricingTier(tierInsideFutureLink).rawValue(new BigDecimal("50.00"))
                    .valueType(ValueType.FEE_ABSOLUTE).bankId(TEST_BANK_ID).build());

            pricingTierRepository.save(tierInsideFutureLink);
            futureComponentTierId[0] = tierInsideFutureLink.getId();

            // 2. The Link determines the date availability
            productPricingLinkRepository.save(ProductPricingLink.builder()
                    .product(persistedProduct)
                    .pricingComponent(futureLinkComponent)
                    .bankId(TEST_BANK_ID)
                    .effectiveDate(LocalDate.now().plusDays(10)) // Future date
                    .useRulesEngine(true).build());
        });

        reloadRules();

        // 3. Requesting pricing for TODAY
        ProductPricingRequest request = ProductPricingRequest.builder()
                .productId(this.persistedProduct.getId())
                .customerSegment(TEST_SEGMENT)
                .effectiveDate(LocalDate.now()).build();

        List<PriceComponentDetail> results = productPricingService.getProductPricing(request).getComponentBreakdown();

        // 4. Verify that the Tier belonging to the future-dated Link did not fire
        boolean ruleFromFutureLinkFired = results.stream()
                .anyMatch(r -> futureComponentTierId[0].equals(r.getMatchedTierId()));

        assertFalse(ruleFromFutureLinkFired,
                "The rule for Tier " + futureComponentTierId[0] + " should not fire because its associated Link is not yet effective.");
    }

    @Test
    @DisplayName("Temporal Gap - Throws NotFound when no Links are active for the requested date")
    void testCalculation_InDateGap_ThrowsNotFound() {
        txHelper.doInTransaction(() -> {
            // Expire all links to create a gap for today
            productPricingLinkRepository.findAll().forEach(link -> {
                link.setExpiryDate(LocalDate.now().minusDays(1));
                productPricingLinkRepository.save(link);
            });
        });

        reloadRules();

        ProductPricingRequest request = ProductPricingRequest.builder()
                .productId(this.persistedProduct.getId())
                .effectiveDate(LocalDate.now())
                .customerSegment(TEST_SEGMENT).build();

        assertThrows(NotFoundException.class, () -> productPricingService.getProductPricing(request));
    }

    @Test
    @DisplayName("Deletion - Rules stop firing immediately after component deletion and reload")
    void testRuleReloadAfterFullComponentDeletion() {
        txHelper.doInTransaction(() -> {
            PricingComponent component = pricingComponentRepository.findByName(TEST_COMPONENT_NAME).orElseThrow();
            Long compId = component.getId();
            productPricingLinkRepository.deleteByPricingComponentId(compId);
            component.getPricingTiers().clear();
            pricingComponentRepository.flush();
            pricingComponentService.deletePricingComponent(compId);
        });
        reloadRules();
        ProductPricingRequest request = ProductPricingRequest.builder()
                .productId(this.persistedProduct.getId()).customerSegment(TEST_SEGMENT).build();

        assertThrows(NotFoundException.class, () -> productPricingService.getProductPricing(request));
    }

    @Test
    @DisplayName("Logic Branch - Percentage discount rule execution")
    void testRuleExecutionForPercentageDiscount() {
        txHelper.doInTransaction(() -> {
            PricingComponent component = pricingComponentRepository.save(PricingComponent.builder()
                    .name("BulkDiscountComponent").code("BULK-DIS-01")
                    .type(PricingComponent.ComponentType.DISCOUNT).bankId(TEST_BANK_ID).build());

            PricingTier tier = PricingTier.builder()
                    .pricingComponent(component).name("Bulk Tier")
                    .minThreshold(BigDecimal.ZERO).bankId(TEST_BANK_ID).build();

            tier.getConditions().add(TierCondition.builder()
                    .pricingTier(tier).attributeName("transactionAmount").operator(Operator.GT)
                    .attributeValue("500.00").connector(TierCondition.LogicalConnector.AND)
                    .bankId(TEST_BANK_ID).build());

            tier.getPriceValues().add(PriceValue.builder()
                    .pricingTier(tier).rawValue(new BigDecimal("5.00"))
                    .valueType(ValueType.DISCOUNT_PERCENTAGE).bankId(TEST_BANK_ID).build());

            pricingTierRepository.save(tier);

            productPricingLinkRepository.save(ProductPricingLink.builder()
                    .product(persistedProduct).pricingComponent(component)
                    .bankId(TEST_BANK_ID).effectiveDate(LocalDate.now())
                    .useRulesEngine(true).build());
        });

        reloadRules();

        ProductPricingRequest request = ProductPricingRequest.builder()
                .productId(this.persistedProduct.getId())
                .customerSegment(TEST_SEGMENT).transactionAmount(TEST_AMOUNT).build();

        List<PriceComponentDetail> results = productPricingService.getProductPricing(request).getComponentBreakdown();

        assertTrue(results.stream().anyMatch(r -> r.getValueType() == ValueType.DISCOUNT_PERCENTAGE),
                "Should have found a percentage discount in the breakdown");
    }

    @Test
    @DisplayName("Management - Reload endpoint security check")
    void testManagementReloadEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/rules/reload")).andExpect(status().isOk());
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