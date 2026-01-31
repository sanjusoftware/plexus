package com.bankengine.pricing.integration;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.data.seeding.CoreMetadataSeeder;
import com.bankengine.pricing.dto.PricingRequest;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.dto.ProductPricingCalculationResult.PriceComponentDetail;
import com.bankengine.pricing.model.*;
import com.bankengine.pricing.model.PriceValue.ValueType;
import com.bankengine.pricing.model.TierCondition.Operator;
import com.bankengine.pricing.repository.*;
import com.bankengine.pricing.service.PricingCalculationService;
import com.bankengine.pricing.service.PricingComponentService;
import com.bankengine.rules.service.KieContainerReloadService;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.bankengine.web.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class DroolsIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_COMPONENT_NAME = "AnnualFeeComponent";
    private static final String TEST_SEGMENT = "RETAIL";
    private static final BigDecimal TEST_AMOUNT = new BigDecimal("1000.00");
    private static final BigDecimal EXPECTED_PRICE_INITIAL = new BigDecimal("10.00");

    private Product persistedProduct;
    private ProductType persistedProductType;
    private Long existingTierId;

    @Autowired
    private PricingTierRepository pricingTierRepository;
    @Autowired
    private PriceValueRepository priceValueRepository;
    @Autowired
    private TierConditionRepository tierConditionRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductTypeRepository productTypeRepository;
    @Autowired
    private PricingComponentRepository pricingComponentRepository;
    @Autowired
    private ProductPricingLinkRepository productPricingLinkRepository;
    @Autowired
    private PricingInputMetadataRepository pricingInputMetadataRepository;
    @Autowired
    private PricingCalculationService pricingCalculationService;
    @Autowired
    private PricingComponentService pricingComponentService;
    @Autowired
    private KieContainerReloadService kieContainerReloadService;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private CoreMetadataSeeder coreMetadataSeeder;
    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        cleanupData();
        transactionTemplate.execute(status -> {
            coreMetadataSeeder.seedCorePricingInputMetadata(TEST_BANK_ID);

            ProductType productType = new ProductType();
            productType.setName("LOAN_TYPE");
            persistedProductType = productTypeRepository.save(productType);

            Product product = new Product();
            product.setName("Test Loan");
            product.setProductType(persistedProductType);
            product.setCategory("RETAIL");
            persistedProduct = productRepository.save(product);

            PricingComponent component = new PricingComponent(TEST_COMPONENT_NAME, PricingComponent.ComponentType.FEE);
            component = pricingComponentRepository.save(component);

            PricingTier tier = new PricingTier(component, "Base Tier", BigDecimal.ZERO, null);
            tier.setEffectiveDate(LocalDate.now().minusDays(1)); // Ensure it's valid for today

            TierCondition condition = new TierCondition();
            condition.setPricingTier(tier);
            condition.setAttributeName("customerSegment");
            condition.setOperator(Operator.EQ);
            condition.setAttributeValue(TEST_SEGMENT);
            condition.setConnector(TierCondition.LogicalConnector.AND);
            tier.getConditions().add(condition);

            PriceValue priceValue = new PriceValue(tier, EXPECTED_PRICE_INITIAL, ValueType.FEE_ABSOLUTE);
            tier.getPriceValues().add(priceValue);
            pricingTierRepository.save(tier);
            this.existingTierId = tier.getId();

            productPricingLinkRepository.save(new ProductPricingLink(persistedProduct, component, null, null, null, true));
            return null;
        });
        kieContainerReloadService.reloadKieContainer(TEST_BANK_ID);
    }

    @AfterEach
    void cleanup() {
        try {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            cleanupData();
        } finally {
            TenantContextHolder.clear();
        }
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
    @WithMockUser(authorities = {"pricing:calculation:read"})
    void testEffectiveDate_FutureRule_NotReturned() {
        final Long[] futureTierId = new Long[1];
        // Use a fixed LocalDate to ensure consistency between DB and Request
        LocalDate today = LocalDate.now();

        transactionTemplate.execute(status -> {
            // 1. Fetch the component
            PricingComponent comp = pricingComponentRepository.findByName(TEST_COMPONENT_NAME).get();

            // 2. Create a tier that explicitly starts TOMORROW
            PricingTier futureTier = new PricingTier(comp, "Future Tier", BigDecimal.ZERO, null);
            futureTier.setEffectiveDate(today.plusDays(1));

            PriceValue val = new PriceValue(futureTier, new BigDecimal("50.00"), ValueType.FEE_ABSOLUTE);
            futureTier.getPriceValues().add(val);

            pricingTierRepository.save(futureTier);
            futureTierId[0] = futureTier.getId();
            return null;
        });

        // 3. FORCE PERSISTENCE CONTEXT CLEARANCE
        // This ensures the service doesn't use cached entities from the setup() phase
        entityManager.clear();

        // 4. Reload KieContainer so the DRL is generated including the new tier
        kieContainerReloadService.reloadKieContainer(TEST_BANK_ID);

        // 5. Requesting for TODAY (which is before the futureTier's start date)
        PricingRequest request = PricingRequest.builder()
                .productId(this.persistedProduct.getId())
                .customerSegment(TEST_SEGMENT)
                .effectiveDate(today)
                .build();

        List<PriceComponentDetail> results = pricingCalculationService.getProductPricing(request).getComponentBreakdown();

        // 6. Verify the future tier ID is NOT in the results
        boolean futureTierFired = results.stream()
                .anyMatch(r -> futureTierId[0].equals(r.getMatchedTierId()));

        assertFalse(futureTierFired, "Rule for Tier " + futureTierId[0] + " should not fire because its effective date is in the future");
    }

    @Test
    @WithMockUser(authorities = {"pricing:calculation:read"})
    void testCalculation_InDateGap_ThrowsNotFound() {
        transactionTemplate.execute(status -> {
            PricingTier tier = pricingTierRepository.findById(existingTierId).get();
            tier.setExpiryDate(LocalDate.now().minusDays(1)); // Expired yesterday
            pricingTierRepository.save(tier);
            return null;
        });

        kieContainerReloadService.reloadKieContainer(TEST_BANK_ID);

        PricingRequest request = PricingRequest.builder()
                .productId(this.persistedProduct.getId())
                .effectiveDate(LocalDate.now())
                .customerSegment(TEST_SEGMENT)
                .build();

        assertThrows(NotFoundException.class, () -> pricingCalculationService.getProductPricing(request));
    }

    @Test
    @WithMockUser(authorities = {"pricing:calculation:read", "pricing:component:delete"})
    void testRuleReloadAfterFullComponentDeletion() {
        // FIXED: Wrap findByName in transaction
        final Long[] ids = new Long[2];
        transactionTemplate.execute(status -> {
            PricingComponent component = pricingComponentRepository.findByName(TEST_COMPONENT_NAME).get();
            ids[0] = component.getId();
            ids[1] = component.getPricingTiers().iterator().next().getId();
            return null;
        });

        pricingComponentService.deleteTierAndValue(ids[0], ids[1]);

        transactionTemplate.execute(status -> {
            productPricingLinkRepository.deleteByPricingComponentId(ids[0]);
            pricingComponentService.deletePricingComponent(ids[0]);
            return null;
        });

        kieContainerReloadService.reloadKieContainer(TEST_BANK_ID);

        PricingRequest request = PricingRequest.builder()
                .productId(this.persistedProduct.getId())
                .customerSegment(TEST_SEGMENT)
                .build();

        assertThrows(NotFoundException.class, () -> pricingCalculationService.getProductPricing(request));
    }

    @Test
    @WithMockUser(authorities = {"pricing:calculation:read"})
    void testStandardRuleExecution_Success() {
        PricingRequest request = PricingRequest.builder()
                .productId(this.persistedProduct.getId())
                .customerSegment(TEST_SEGMENT)
                .amount(TEST_AMOUNT)
                .build();

        ProductPricingCalculationResult result = pricingCalculationService.getProductPricing(request);
        assertEquals(EXPECTED_PRICE_INITIAL, result.getComponentBreakdown().get(0).getRawValue());
    }

    @Test
    @WithMockUser(authorities = {"pricing:calculation:read", "pricing:component:write"})
    void testRuleExecutionForPercentageDiscount() {
        transactionTemplate.execute(status -> {
            PricingComponent component = new PricingComponent("BulkDiscountComponent", PricingComponent.ComponentType.DISCOUNT);
            component = pricingComponentRepository.save(component);

            PricingTier tier = new PricingTier(component, "Bulk Discount Tier", BigDecimal.ZERO, null);
            tier.setEffectiveDate(LocalDate.now().minusDays(1));

            TierCondition cond = new TierCondition(tier, "transactionAmount", Operator.GT, "500.00", TierCondition.LogicalConnector.AND);
            tier.getConditions().add(cond);

            PriceValue val = new PriceValue(tier, new BigDecimal("5.00"), ValueType.DISCOUNT_PERCENTAGE);
            tier.getPriceValues().add(val);
            pricingTierRepository.save(tier);

            productPricingLinkRepository.save(new ProductPricingLink(persistedProduct, component, null, null, null, true));
            return null;
        });

        kieContainerReloadService.reloadKieContainer(TEST_BANK_ID);

        PricingRequest request = PricingRequest.builder()
                .productId(this.persistedProduct.getId())
                .customerSegment(TEST_SEGMENT)
                .amount(TEST_AMOUNT)
                .build();

        List<PriceComponentDetail> results = pricingCalculationService.getProductPricing(request).getComponentBreakdown();
        assertTrue(results.stream().anyMatch(r -> r.getValueType() == ValueType.DISCOUNT_PERCENTAGE));
    }

    @Test
    @WithMockUser(authorities = {"rules:management:reload"})
    void testManagementReloadEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/rules/reload")).andExpect(status().isOk());
    }
}