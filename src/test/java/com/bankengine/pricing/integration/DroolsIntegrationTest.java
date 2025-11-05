package com.bankengine.pricing.integration;

import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PriceValue.ValueType;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.TierCondition;
import com.bankengine.pricing.model.TierCondition.LogicalConnector;
import com.bankengine.pricing.model.TierCondition.Operator;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.repository.ProductPricingLinkRepository;
import com.bankengine.rules.model.PricingInput;
import com.bankengine.rules.service.KieContainerReloadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.KieSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
public class DroolsIntegrationTest {

    @Autowired
    private PricingComponentRepository componentRepository;

    @Autowired
    private ProductPricingLinkRepository productPricingLinkRepository;

    @Autowired
    private KieContainerReloadService kieContainerReloadService;

    private static final String TEST_COMPONENT_NAME = "FeeComponent";
    private static final String TEST_SEGMENT = "PREMIUM";
    private static final BigDecimal TEST_AMOUNT = new BigDecimal("100000.00");
    private static final BigDecimal EXPECTED_PRICE = new BigDecimal("10.00");

    @BeforeEach
    void setUp() {
        productPricingLinkRepository.deleteAll();
        componentRepository.deleteAll();
        seedDatabaseWithTestRule();
        assertTrue(kieContainerReloadService.reloadKieContainer(),
                "KieContainer must reload successfully after data seeding.");
    }

    private void seedDatabaseWithTestRule() {
        // --- 1. Price Value ---
        PriceValue priceValue = new PriceValue();
        priceValue.setPriceAmount(EXPECTED_PRICE);
        priceValue.setValueType(ValueType.ABSOLUTE);
        priceValue.setCurrency("USD");

        // --- 2. Tier Condition: customerSegment == "PREMIUM" ---
        TierCondition condition = new TierCondition();
        condition.setAttributeName("customerSegment");
        condition.setOperator(Operator.EQ);
        condition.setAttributeValue(TEST_SEGMENT);
        condition.setConnector(LogicalConnector.AND);

        // --- 3. Pricing Tier ---
        PricingTier tier = new PricingTier();
        tier.setTierName("Premium Tier");
        tier.setConditions(List.of(condition));
        tier.setPriceValues(List.of(priceValue));

        // Set bidirectional relationships
        condition.setPricingTier(tier);
        priceValue.setPricingTier(tier);

        // --- 4. Pricing Component ---
        PricingComponent component = new PricingComponent();
        component.setName(TEST_COMPONENT_NAME);
        component.setType(PricingComponent.ComponentType.FEE);
        component.setPricingTiers(List.of(tier));

        // Set bidirectional relationship
        tier.setPricingComponent(component);

        // Save the component and all cascading entities
        componentRepository.save(component);
    }

    /**
     * Test the full pipeline: DB Data -> DRL Generation -> KieContainer Compilation -> Rule Firing.
     */
    @Test
    void testDbRulesFireSuccessfully() {
        // Get the RELOADED KieContainer from the service
        KieSession kieSession = kieContainerReloadService.getKieContainer().newKieSession();

        // Input fact that should match the rule (customerSegment == "PREMIUM")
        PricingInput input = new PricingInput();
        input.setCustomerSegment(TEST_SEGMENT);
        input.setTransactionAmount(TEST_AMOUNT);

        // ACT
        kieSession.insert(input);
        int rulesFired = kieSession.fireAllRules();
        kieSession.dispose();

        // ASSERT
        assertEquals(1, rulesFired, "Expected exactly one rule to fire, matching the DB configuration.");
        assertTrue(input.isRuleFired(), "The rule action should have set the ruleFired flag to true.");
        assertEquals(EXPECTED_PRICE, input.getPriceAmount(), "The price amount should be set by the rule from the DB.");
        assertEquals("USD", input.getCurrency(), "The currency should be set by the rule from the DB.");
    }
}