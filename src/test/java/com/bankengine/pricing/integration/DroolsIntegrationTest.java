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

    private static final String TEST_COMPONENT_NAME = "AnnualFeeComponent";
    private static final String TEST_SEGMENT = "RETAIL";
    private static final BigDecimal TEST_AMOUNT = new BigDecimal("1000.00");
    private static final BigDecimal EXPECTED_PRICE_INITIAL = new BigDecimal("10.00");

    private Product persistedProduct;
    private ProductType persistedProductType;

    @Autowired private PricingTierRepository pricingTierRepository;
    @Autowired private PriceValueRepository priceValueRepository;
    @Autowired private TierConditionRepository tierConditionRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductTypeRepository productTypeRepository;
    @Autowired private PricingComponentRepository pricingComponentRepository;
    @Autowired private ProductPricingLinkRepository productPricingLinkRepository;
    @Autowired private PricingInputMetadataRepository pricingInputMetadataRepository;
    @Autowired private PricingCalculationService pricingCalculationService;
    @Autowired private PricingComponentService pricingComponentService;
    @Autowired private KieContainerReloadService kieContainerReloadService;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private CoreMetadataSeeder coreMetadataSeeder;

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

            productPricingLinkRepository.save(new ProductPricingLink(
                    persistedProduct, component, null, true
            ));

            entityManager.flush();
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
        tierConditionRepository.deleteAllInBatch();
        priceValueRepository.deleteAllInBatch();
        pricingTierRepository.deleteAllInBatch();
        pricingComponentRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        productTypeRepository.deleteAllInBatch();

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
        List<PriceComponentDetail> components = result.getComponentBreakdown();

        assertFalse(components.isEmpty(), "Expected at least one price component result.");
        PriceComponentDetail firstComponent = components.get(0);

        assertEquals(EXPECTED_PRICE_INITIAL, firstComponent.getAmount(), "Rule execution must succeed.");
        assertEquals(ValueType.ABSOLUTE.name(), firstComponent.getValueType().name());
    }

    @Test
    @WithMockUser(authorities = {"pricing:calculation:read", "pricing:metadata:write", "pricing:component:write"})
    void testRuleExecutionWithCustomInputAttribute() {
        transactionTemplate.execute(status -> {
            PricingInputMetadata metadata = new PricingInputMetadata();
            metadata.setAttributeKey("CLIENT_AGE");
            metadata.setDataType("INTEGER");
            metadata.setDisplayName("Client Age for Pricing");
            pricingInputMetadataRepository.save(metadata);

            String customCompName = "AgeRuleComponent";
            PricingComponent component = new PricingComponent(customCompName, PricingComponent.ComponentType.FEE);
            PricingComponent persistedComponent = pricingComponentRepository.save(component);

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

            productPricingLinkRepository.save(new ProductPricingLink(
                    persistedProduct, persistedComponent, null, true
            ));

            entityManager.flush();
            return null;
        });

        kieContainerReloadService.reloadKieContainer(TEST_BANK_ID);

        PricingRequest successRequest = PricingRequest.builder()
                .productId(this.persistedProduct.getId())
                .customerSegment(TEST_SEGMENT)
                .amount(TEST_AMOUNT)
                .customAttributes(Collections.singletonMap("CLIENT_AGE", 65L))
                .build();

        List<PriceComponentDetail> successResults = pricingCalculationService.getProductPricing(successRequest).getComponentBreakdown();

        assertEquals(2, successResults.size(), "Expected two price facts.");
        assertTrue(successResults.stream().anyMatch(r -> r.getAmount().compareTo(new BigDecimal("20.00")) == 0));
        assertTrue(successResults.stream().anyMatch(r -> r.getAmount().compareTo(new BigDecimal("10.00")) == 0));

        PricingRequest failureRequest = PricingRequest.builder()
                .productId(this.persistedProduct.getId())
                .customerSegment(TEST_SEGMENT)
                .amount(TEST_AMOUNT)
                .customAttributes(Collections.singletonMap("CLIENT_AGE", 55L))
                .build();

        List<PriceComponentDetail> failureResults = pricingCalculationService.getProductPricing(failureRequest).getComponentBreakdown();

        assertEquals(1, failureResults.size(), "Only the Annual Fee rule should fire, resulting in 1 fact.");
        assertEquals(new BigDecimal("10.00"), failureResults.get(0).getAmount(), "Only the $10.00 Annual Fee must be present.");
    }

    @Test
    @WithMockUser(authorities = {"pricing:calculation:read", "pricing:component:write"})
    void testRuleExecutionForFreeCountBenefit() {
        transactionTemplate.execute(status -> {
            String benefitCompName = "FreeATMComponent";
            PricingComponent component = new PricingComponent(benefitCompName, PricingComponent.ComponentType.BENEFIT);
            component = pricingComponentRepository.save(component);

            PricingTier tier = new PricingTier(component, "Free ATM Benefit", BigDecimal.ZERO, null);

            TierCondition condition = new TierCondition();
            condition.setPricingTier(tier);
            condition.setAttributeName("customerSegment");
            condition.setOperator(Operator.EQ);
            condition.setAttributeValue(TEST_SEGMENT);
            condition.setConnector(TierCondition.LogicalConnector.AND);
            tier.getConditions().add(condition);

            PriceValue freeCountValue = new PriceValue(tier, new BigDecimal("5"), ValueType.FREE_COUNT);
            tier.getPriceValues().add(freeCountValue);

            component.getPricingTiers().add(tier);
            pricingComponentRepository.save(component);

            productPricingLinkRepository.save(new ProductPricingLink(
                    persistedProduct, component, null, true
            ));

            entityManager.flush();
            return null;
        });

        kieContainerReloadService.reloadKieContainer(TEST_BANK_ID);

        PricingRequest request = PricingRequest.builder()
                .productId(this.persistedProduct.getId())
                .customerSegment(TEST_SEGMENT)
                .amount(TEST_AMOUNT)
                .build();

        List<PriceComponentDetail> results = pricingCalculationService.getProductPricing(request).getComponentBreakdown();

        assertEquals(2, results.size(), "Expected two price components (Fee + Benefit).");

        Optional<PriceComponentDetail> freeCountResult = results.stream()
                .filter(r -> ValueType.FREE_COUNT.name().equals(r.getValueType().name()))
                .findFirst();

        assertTrue(freeCountResult.isPresent(), "The FREE_COUNT benefit must be present in the results.");
        assertTrue(new BigDecimal("5").compareTo(freeCountResult.get().getAmount()) == 0, "FREE_COUNT amount must be 5.");
    }

    @Test
    @WithMockUser(authorities = {"pricing:component:delete"})
    void testRuleReloadAfterFullComponentDeletion() {
        final PricingComponent[] componentRef = new PricingComponent[1];

        transactionTemplate.execute(status -> {
            PricingComponent component = pricingComponentRepository.findByName(TEST_COMPONENT_NAME).orElseThrow(
                    () -> new RuntimeException("Setup component not found.")
            );
            componentRef[0] = component;
            return null;
        });

        Long componentId = componentRef[0].getId();

        Optional<PricingTier> tierOptional = transactionTemplate.execute(status ->
                pricingComponentRepository.findById(componentId).orElseThrow(() -> new RuntimeException("Component not found"))
                        .getPricingTiers().stream().findFirst()
        );
        Long tierId = tierOptional.orElseThrow(() -> new RuntimeException("Tier not found for component")).getId();

        PricingRequest baselineRequest = PricingRequest.builder()
                .productId(this.persistedProduct.getId())
                .customerSegment(TEST_SEGMENT)
                .amount(TEST_AMOUNT)
                .build();

        List<PriceComponentDetail> resultsBefore = pricingCalculationService.getProductPricing(baselineRequest).getComponentBreakdown();
        assertFalse(resultsBefore.isEmpty());
        assertEquals(EXPECTED_PRICE_INITIAL, resultsBefore.get(0).getAmount());

        pricingComponentService.deleteTierAndValue(componentId, tierId);

        Assertions.assertEquals(0L, getTierCountDirectly(componentId),
                "Database tier count must be zero after first deletion transaction commits.");

        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                jdbcTemplate.update("DELETE FROM PRODUCT_PRICING_LINK WHERE PRICING_COMPONENT_ID = ?", componentId);
                entityManager.clear();
                pricingComponentService.deletePricingComponent(componentId);
            }
        });

        kieContainerReloadService.reloadKieContainer(TEST_BANK_ID);

        PricingRequest failRequest = PricingRequest.builder()
                .productId(this.persistedProduct.getId())
                .customerSegment(TEST_SEGMENT)
                .amount(TEST_AMOUNT)
                .build();

        assertThrows(NotFoundException.class,
                () -> pricingCalculationService.getProductPricing(failRequest),
                "Rule execution must now throw NotFoundException after rule deletion.");
    }

    @Test
    @WithMockUser(authorities = {"rules:management:reload"})
    void testManagementReloadEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/rules/reload"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"pricing:calculation:read", "pricing:component:write"})
    void testRuleExecutionForPercentageDiscount() {
        transactionTemplate.execute(status -> {
            String discountCompName = "BulkDiscountComponent";
            PricingComponent component = new PricingComponent(discountCompName, PricingComponent.ComponentType.DISCOUNT);
            component = pricingComponentRepository.save(component);

            PricingTier tier = new PricingTier(component, "Bulk Discount Tier", BigDecimal.ZERO, null);

            TierCondition condition = new TierCondition();
            condition.setPricingTier(tier);
            condition.setAttributeName("transactionAmount");
            condition.setOperator(Operator.GT);
            condition.setAttributeValue("500.00");
            condition.setConnector(TierCondition.LogicalConnector.AND);
            tier.getConditions().add(condition);

            PriceValue discountValue = new PriceValue(tier, new BigDecimal("5.00"), ValueType.DISCOUNT_PERCENTAGE);
            tier.getPriceValues().add(discountValue);

            component.getPricingTiers().add(tier);
            pricingComponentRepository.save(component);

            productPricingLinkRepository.save(new ProductPricingLink(
                    persistedProduct, component, null, true
            ));

            entityManager.flush();
            return null;
        });

        kieContainerReloadService.reloadKieContainer(TEST_BANK_ID);

        PricingRequest successRequest = PricingRequest.builder()
                .productId(this.persistedProduct.getId())
                .customerSegment(TEST_SEGMENT)
                .amount(TEST_AMOUNT)
                .build();

        List<PriceComponentDetail> successResults = pricingCalculationService.getProductPricing(successRequest).getComponentBreakdown();

        Optional<PriceComponentDetail> discountResult = successResults.stream()
                .filter(r -> ValueType.DISCOUNT_PERCENTAGE.name().equals(r.getValueType().name()))
                .findFirst();

        assertTrue(discountResult.isPresent(), "The DISCOUNT_PERCENTAGE benefit must be present in the results.");
        assertEquals(new BigDecimal("5.00"), discountResult.get().getAmount(), "DISCOUNT_PERCENTAGE amount must be 5.00.");

        PricingRequest failureRequest = PricingRequest.builder()
                .productId(this.persistedProduct.getId())
                .customerSegment(TEST_SEGMENT)
                .amount(new BigDecimal("400.00"))
                .build();

        List<PriceComponentDetail> failureResults = pricingCalculationService.getProductPricing(failureRequest).getComponentBreakdown();
        assertEquals(1, failureResults.size(), "Only the baseline rule should fire.");
        assertEquals(EXPECTED_PRICE_INITIAL, failureResults.get(0).getAmount());
    }

    private long getTierCountDirectly(Long componentId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(id) FROM pricing_tier WHERE component_id = ?", Long.class, componentId);
    }
}