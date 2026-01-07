package com.bankengine.pricing.integration;

import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.data.seeding.CoreMetadataSeeder;
import com.bankengine.pricing.dto.PriceRequest;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class DroolsIntegrationTest extends AbstractIntegrationTest {

    // --- SETUP VARIABLES ---
    private static final String TEST_COMPONENT_NAME = "AnnualFeeComponent";
    private static final String TEST_SEGMENT = "RETAIL";
    private static final BigDecimal TEST_AMOUNT = new BigDecimal("1000.00");
    private static final BigDecimal EXPECTED_PRICE_INITIAL = new BigDecimal("10.00");
    private Product persistedProduct;
    private ProductType persistedProductType;

    // --- REPOSITORIES & SERVICES ---
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
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private CoreMetadataSeeder coreMetadataSeeder;

    @BeforeEach
    void setup() {
        cleanupData();

        transactionTemplate.execute(status -> {
            coreMetadataSeeder.seedCorePricingInputMetadata();

            // 1. Setup Product Type
            ProductType productType = new ProductType();
            productType.setName("LOAN_TYPE");
            persistedProductType = productTypeRepository.save(productType);

            // 2. Setup Product
            Product product = new Product();
            product.setName("Test Loan");
            product.setProductType(persistedProductType);
            product.setCategory("RETAIL");
            persistedProduct = productRepository.save(product);

            // 3. Setup Pricing Component Graph: A simple Annual Fee (10.00) if segment is RETAIL
            PricingComponent component = new PricingComponent(TEST_COMPONENT_NAME, PricingComponent.ComponentType.FEE);
            component = pricingComponentRepository.save(component);

            PricingTier tier = new PricingTier(component, "Base Tier", BigDecimal.ZERO, null);

            TierCondition condition = new TierCondition();
            condition.setPricingTier(tier);
            condition.setAttributeName("customerSegment");
            condition.setOperator(Operator.EQ);
            condition.setAttributeValue(TEST_SEGMENT);
            condition.setConnector(TierCondition.LogicalConnector.AND);
            tier.getConditions().add(condition);

            PriceValue priceValue = new PriceValue(tier, EXPECTED_PRICE_INITIAL, ValueType.ABSOLUTE);
            tier.getPriceValues().add(priceValue);

            component.getPricingTiers().add(tier);
            pricingComponentRepository.save(component);

            // 4. Link Product to Pricing Component
            productPricingLinkRepository.save(new ProductPricingLink(
                    persistedProduct,
                    component,
                    "ANNUAL_FEE",
                    null,
                    true // Use Rules Engine
            ));

            entityManager.flush();
            return null;
        });

        // 5. Build and Deploy Rules (must be outside the persistence transaction)
        boolean success = kieContainerReloadService.reloadKieContainer(TEST_BANK_ID);
        assertTrue(success, "KieContainer must reload successfully during setup.");
    }

    @AfterEach
    void cleanup() {
        cleanupData();
    }

    private void cleanupData() {
        productPricingLinkRepository.deleteAllInBatch();
        tierConditionRepository.deleteAllInBatch();
        priceValueRepository.deleteAllInBatch();
        pricingTierRepository.deleteAllInBatch();
        pricingComponentRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        productTypeRepository.deleteAllInBatch();
//        pricingInputMetadataRepository.deleteAllInBatch();
    }

    /**
     * 1. Standard Rule Execution Integration Test (Baseline Check)
     */
    @Test
    @WithMockUser(authorities = {"pricing:calculation:read"})
    void testStandardRuleExecution_Success() {
        PriceRequest request = PriceRequest.builder()
                .productId(this.persistedProduct.getId())
                .customerSegment(TEST_SEGMENT)
                .amount(TEST_AMOUNT)
                .build();

        ProductPricingCalculationResult result = pricingCalculationService.getProductPricing(request);
        List<PriceComponentDetail> components = result.getComponentBreakdown();

        // ASSERT
        assertFalse(components.isEmpty(), "Expected at least one price component result.");
        PriceComponentDetail firstComponent = components.get(0);

        // Use .getAmount() instead of .getPriceAmount()
        assertEquals(EXPECTED_PRICE_INITIAL, firstComponent.getAmount(), "Rule execution must succeed.");
        assertEquals(ValueType.ABSOLUTE.name(), firstComponent.getValueType().name());
    }

    /**
     * 2. Test Rule Execution with Custom Input Attribute
     */
    @Test
    @WithMockUser(authorities = {"pricing:calculation:read", "pricing:metadata:write", "pricing:component:write"})
    void testRuleExecutionWithCustomInputAttribute() {
        transactionTemplate.execute(status -> {
            // ARRANGE 1: Setup custom metadata input
            PricingInputMetadata metadata = new PricingInputMetadata();
            metadata.setAttributeKey("CLIENT_AGE");
            metadata.setDataType("INTEGER");
            metadata.setDisplayName("Client Age for Pricing");
            pricingInputMetadataRepository.save(metadata);

            // ARRANGE 2: Create a new component graph that uses the custom attribute
            String customCompName = "AgeRuleComponent";
            PricingComponent component = new PricingComponent(customCompName, PricingComponent.ComponentType.FEE);
            PricingComponent persistedComponent = pricingComponentRepository.save(component);

            // Tier condition: IF CLIENT_AGE > 60 THEN $20.00
            PricingTier tier = new PricingTier(persistedComponent, "Senior Tier", BigDecimal.ZERO, null);

            TierCondition condition = new TierCondition();
            condition.setPricingTier(tier);
            condition.setAttributeName("CLIENT_AGE");
            condition.setOperator(Operator.GT);
            condition.setAttributeValue("60");
            condition.setConnector(TierCondition.LogicalConnector.AND);
            tier.getConditions().add(condition);

            PriceValue priceValue = new PriceValue(tier, new BigDecimal("20.00"), ValueType.ABSOLUTE);
            tier.getPriceValues().add(priceValue);

            persistedComponent.getPricingTiers().add(tier);
            pricingComponentRepository.save(persistedComponent);

            // Link component to product (using the same product from setup)
            productPricingLinkRepository.save(new ProductPricingLink(
                    persistedProduct,
                    persistedComponent,
                    "AGE_RULE",
                    null,
                    true
            ));

            entityManager.flush();
            return null;
        });

        assertTrue(kieContainerReloadService.reloadKieContainer(TEST_BANK_ID), "KieContainer must reload successfully with new custom rule.");

        // ACT & ASSERT 1: Test Success (Age 65 > 60)
        PriceRequest successRequest = PriceRequest.builder()
                .productId(this.persistedProduct.getId())
                .customerSegment(TEST_SEGMENT)
                .amount(TEST_AMOUNT)
                .customAttributes(Collections.singletonMap("CLIENT_AGE", 65L))
                .build();

        List<PriceComponentDetail> successResults = pricingCalculationService.getProductPricing(successRequest).getComponentBreakdown();

        assertEquals(2, successResults.size(), "Expected two price facts.");
        assertTrue(successResults.stream().anyMatch(r -> r.getAmount().compareTo(new BigDecimal("20.00")) == 0));
        assertTrue(successResults.stream().anyMatch(r -> r.getAmount().compareTo(new BigDecimal("10.00")) == 0));

        PriceRequest failureRequest = PriceRequest.builder()
                .productId(this.persistedProduct.getId())
                .customerSegment(TEST_SEGMENT)
                .amount(TEST_AMOUNT)
                .customAttributes(Collections.singletonMap("CLIENT_AGE", 55L))
                .build();

        List<PriceComponentDetail> failureResults = pricingCalculationService.getProductPricing(failureRequest).getComponentBreakdown();

        assertEquals(1, failureResults.size(), "Only the Annual Fee rule should fire, resulting in 1 fact.");
        assertEquals(new BigDecimal("10.00"), failureResults.get(0).getAmount(), "Only the $10.00 Annual Fee must be present.");
    }

    /**
     * 3. Test Rule Execution for New FREE_COUNT Benefit
     */
    @Test
    @WithMockUser(authorities = {"pricing:calculation:read", "pricing:component:write"})
    void testRuleExecutionForFreeCountBenefit() {
        transactionTemplate.execute(status -> {
            // ARRANGE: Create a new component graph that provides a FREE_COUNT benefit
            String benefitCompName = "FreeATMComponent";
            PricingComponent component = new PricingComponent(benefitCompName, PricingComponent.ComponentType.BENEFIT);
            component = pricingComponentRepository.save(component);

            // Tier condition: IF customerSegment == RETAIL THEN FREE_COUNT = 5
            PricingTier tier = new PricingTier(component, "Free ATM Benefit", BigDecimal.ZERO, null);

            TierCondition condition = new TierCondition();
            condition.setPricingTier(tier);
            condition.setAttributeName("customerSegment");
            condition.setOperator(Operator.EQ);
            condition.setAttributeValue(TEST_SEGMENT);
            condition.setConnector(TierCondition.LogicalConnector.AND);
            tier.getConditions().add(condition);

            // Output the new FREE_COUNT type
            PriceValue freeCountValue = new PriceValue(tier, new BigDecimal("5"), ValueType.FREE_COUNT);
            tier.getPriceValues().add(freeCountValue);

            component.getPricingTiers().add(tier);
            pricingComponentRepository.save(component);

            // Link component to product (using the same product from setup)
            productPricingLinkRepository.save(new ProductPricingLink(
                    persistedProduct,
                    component,
                    "ATM_BENEFIT",
                    null,
                    true
            ));

            entityManager.flush();
            return null;
        }); // Transaction commits here

        // Reload Rules
        assertTrue(kieContainerReloadService.reloadKieContainer(TEST_BANK_ID), "KieContainer must reload successfully with new FREE_COUNT rule.");

        // ACT
        PriceRequest request = PriceRequest.builder()
                .productId(this.persistedProduct.getId())
                .customerSegment(TEST_SEGMENT)
                .amount(TEST_AMOUNT)
                .build();

        List<PriceComponentDetail> results = pricingCalculationService.getProductPricing(request).getComponentBreakdown();

        // ASSERT
        // Should return the Annual Fee (10.00) AND the Free Count (5)
        assertEquals(2, results.size(), "Expected two price components (Fee + Benefit).");

        Optional<PriceComponentDetail> freeCountResult = results.stream()
                .filter(r -> ValueType.FREE_COUNT.name().equals(r.getValueType().name()))
                .findFirst();

        assertTrue(freeCountResult.isPresent(), "The FREE_COUNT benefit must be present in the results.");
        assertTrue(new BigDecimal("5").compareTo(freeCountResult.get().getAmount()) == 0, "FREE_COUNT amount must be 5.");
    }

    /**
     * 4. Test Rule Deletion and Fallback
     */
    @Test
    @WithMockUser(authorities = {"pricing:component:delete"})
    void testRuleReloadAfterFullComponentDeletion() {
        // ARRANGE: Get the committed component's ID
        final PricingComponent[] componentRef = new PricingComponent[1];

        // Fetch component details in a read-only transaction for isolation
        transactionTemplate.execute(status -> {
            PricingComponent component = pricingComponentRepository.findByName(TEST_COMPONENT_NAME).orElseThrow(
                    () -> new RuntimeException("Setup component not found.")
            );
            componentRef[0] = component;
            return null;
        });

        // Extract the component and IDs
        Long componentId = componentRef[0].getId();

        Optional<PricingTier> tierOptional = transactionTemplate.execute(status ->
                pricingComponentRepository.findById(componentId).orElseThrow(() -> new RuntimeException("Component not found"))
                        .getPricingTiers().stream().findFirst()
        );
        Long tierId = tierOptional.orElseThrow(() -> new RuntimeException("Tier not found for component")).getId();

        // ACT 1: Execute baseline check
        PriceRequest baselineRequest = PriceRequest.builder()
                .productId(this.persistedProduct.getId())
                .customerSegment(TEST_SEGMENT)
                .amount(TEST_AMOUNT)
                .build();

        List<PriceComponentDetail> resultsBefore = pricingCalculationService.getProductPricing(baselineRequest).getComponentBreakdown();
        assertFalse(resultsBefore.isEmpty());
        assertEquals(EXPECTED_PRICE_INITIAL, resultsBefore.get(0).getAmount());

        // ACT 2: Delete the Tier (Runs in REQUIRES_NEW and COMMITS deletion)
        pricingComponentService.deleteTierAndValue(componentId, tierId);

        Assertions.assertEquals(0L, getTierCountDirectly(componentId),
                "Database tier count must be zero after first deletion transaction commits.");

        // Delete the ProductPricingLink and COMMIT it immediately
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                // Delete dependency using direct JDBC
                jdbcTemplate.update(
                        "DELETE FROM PRODUCT_PRICING_LINK WHERE PRICING_COMPONENT_ID = ?",
                        componentId
                );
            }
        });

        // ACT 3: Delete the component in a new transaction
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                entityManager.clear();
                pricingComponentService.deletePricingComponent(componentId);
            }
        });

        // ACT 4: Perform reload (Optional stability check)
        boolean success = kieContainerReloadService.reloadKieContainer(TEST_BANK_ID);
        assertTrue(success, "KieContainer must reload successfully after component deletion.");

        // ASSERT: Calculation should now fail (rules are deleted)
        PriceRequest failRequest = PriceRequest.builder()
                .productId(this.persistedProduct.getId())
                .customerSegment(TEST_SEGMENT)
                .amount(TEST_AMOUNT)
                .build();

        assertThrows(NotFoundException.class,
                () -> pricingCalculationService.getProductPricing(failRequest),
                "Rule execution must now throw NotFoundException after rule deletion.");
    }

    /**
     * 5. Test Management Reload Endpoint Integration Test
     */
    @Test
    @WithMockUser(authorities = {"rules:management:reload"})
    void testManagementReloadEndpoint() throws Exception {
        // ARRANGE: URL for the Rule Management Controller
        String reloadUrl = "/api/v1/rules/reload";

        // ACT & ASSERT: Make a POST request, simulating an authenticated user with authority
        mockMvc.perform(post(reloadUrl))
                .andExpect(status().isOk());
    }

    /**
     * 6. Test Rule Execution for Percentage Discount
     */
    @Test
    @WithMockUser(authorities = {"pricing:calculation:read", "pricing:component:write"})
    void testRuleExecutionForPercentageDiscount() {
        transactionTemplate.execute(status -> {
            // ARRANGE: Create a new component graph that provides a 5% discount rate if amount > 500.00
            String discountCompName = "BulkDiscountComponent";
            PricingComponent component = new PricingComponent(discountCompName, PricingComponent.ComponentType.DISCOUNT);
            component = pricingComponentRepository.save(component);

            // Tier condition: IF amount > 500.00 THEN DISCOUNT_PERCENTAGE = 5.00
            PricingTier tier = new PricingTier(component, "Bulk Discount Tier", BigDecimal.ZERO, null);

            TierCondition condition = new TierCondition();
            condition.setPricingTier(tier);
            condition.setAttributeName("transactionAmount"); // CRITICAL: Use the core 'amount' attribute from PriceRequest
            condition.setOperator(Operator.GT);
            condition.setAttributeValue("500.00");
            condition.setConnector(TierCondition.LogicalConnector.AND);
            tier.getConditions().add(condition);

            // Output the DISCOUNT_PERCENTAGE type
            PriceValue discountValue = new PriceValue(tier, new BigDecimal("5.00"), ValueType.DISCOUNT_PERCENTAGE);
            tier.getPriceValues().add(discountValue);

            component.getPricingTiers().add(tier);
            pricingComponentRepository.save(component);

            // Link component to product (using the same product from setup)
            productPricingLinkRepository.save(new ProductPricingLink(
                    persistedProduct,
                    component,
                    "BULK_DISCOUNT",
                    null,
                    true
            ));

            entityManager.flush();
            return null;
        }); // Transaction commits here

        // Reload Rules
        assertTrue(kieContainerReloadService.reloadKieContainer(TEST_BANK_ID), "KieContainer must reload successfully with new percentage discount rule.");

        // ACT & ASSERT 1: Test Success (Amount 1000.00 > 500.00)
        PriceRequest successRequest = PriceRequest.builder()
                .productId(this.persistedProduct.getId())
                .customerSegment(TEST_SEGMENT)
                .amount(TEST_AMOUNT)
                .build();

        // FIX: Extract from breakdown
        List<PriceComponentDetail> successResults = pricingCalculationService.getProductPricing(successRequest).getComponentBreakdown();

        Optional<PriceComponentDetail> discountResult = successResults.stream()
                .filter(r -> ValueType.DISCOUNT_PERCENTAGE.name().equals(r.getValueType().name()))
                .findFirst();

        assertTrue(discountResult.isPresent(), "The DISCOUNT_PERCENTAGE benefit must be present in the results.");
        assertEquals(new BigDecimal("5.00"), discountResult.get().getAmount(), "DISCOUNT_PERCENTAGE amount must be 5.00.");

        PriceRequest failureRequest = PriceRequest.builder()
                .productId(this.persistedProduct.getId())
                .customerSegment(TEST_SEGMENT)
                .amount(new BigDecimal("400.00"))
                .build();

        List<PriceComponentDetail> failureResults = pricingCalculationService.getProductPricing(failureRequest).getComponentBreakdown();
        assertEquals(1, failureResults.size(), "Only the baseline rule should fire.");
        assertEquals(EXPECTED_PRICE_INITIAL, failureResults.get(0).getAmount());
    }

    // --- JDBC Helpers (For verification only) ---
    private long getTierCountDirectly(Long componentId) {
        // This query bypasses ALL JPA/Hibernate layers and hits the DB directly
        String sql = "SELECT COUNT(id) FROM pricing_tier WHERE component_id = ?";
        return jdbcTemplate.queryForObject(sql, Long.class, componentId);
    }
}