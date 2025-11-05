package com.bankengine.pricing.service;

import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.TierCondition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DroolsRuleBuilderServiceTest {

    @Autowired
    private DroolsRuleBuilderService ruleBuilderService;

    @Test
    void buildPlaceholderRules_shouldReturnValidRuleContent() {
        // ACT
        String ruleContent = ruleBuilderService.buildPlaceholderRules();

        // ASSERT
        assertNotNull(ruleContent, "Rule content should not be null.");
        assertFalse(ruleContent.trim().isEmpty(), "Rule content should not be empty.");
        assertTrue(ruleContent.contains("rule \"PlaceholderRule_DoNothing\""),
                "Rule content should contain the expected placeholder rule definition.");
    }

    /**
     * Test to ensure complex rule content is generated correctly from a PricingComponent.
     * Uses fields that exist in the provided PricingInput: transactionAmount and customerSegment.
     */
    @Test
    void buildRules_shouldGenerateValidDrl() {
        // ARRANGE: Create a complex, realistic PricingComponent structure

        // 1. Setup Price Value
        PriceValue priceValue = new PriceValue();
        priceValue.setPriceAmount(new BigDecimal("99.99"));
        priceValue.setValueType(PriceValue.ValueType.ABSOLUTE);
        priceValue.setCurrency("EUR");

        // 2. Setup Condition 1 (DECIMAL: transactionAmount > 50000 AND)
        TierCondition condition1 = new TierCondition();
        condition1.setAttributeName("transactionAmount"); // Matches field in PricingInput
        condition1.setOperator(TierCondition.Operator.GT);
        condition1.setAttributeValue("50000.00");
        condition1.setConnector(TierCondition.LogicalConnector.AND); // AND to the next condition

        // 3. Setup Condition 2 (STRING: customerSegment IN ('A', 'B'))
        TierCondition condition2 = new TierCondition();
        condition2.setAttributeName("customerSegment"); // Matches field in PricingInput
        condition2.setOperator(TierCondition.Operator.IN);
        condition2.setAttributeValue("PREMIUM, BUSINESS"); // Comma-separated list
        condition2.setConnector(TierCondition.LogicalConnector.OR);

        // 4. Setup Pricing Tier
        PricingTier tier = new PricingTier();
        tier.setId(50L);
        tier.setTierName("Corporate Discount Tier");
        tier.setConditions(List.of(condition1, condition2)); // Add multiple conditions
        tier.setPriceValues(List.of(priceValue));

        // 5. Setup Pricing Component
        PricingComponent component = new PricingComponent();
        component.setName("Corporate Discount");
        component.setPricingTiers(List.of(tier));

        // ACT: Generate the DRL
        String drlContent = ruleBuilderService.buildRules(component);

        // ASSERT: Check for necessary DRL components
        assertNotNull(drlContent, "DRL content should not be null.");

        // --- 1. Rule Name & Structure ---
        String expectedRuleName = "Rule_Corporate_Discount_Tier_50";
        assertTrue(drlContent.contains("rule \"" + expectedRuleName + "\""), "DRL should contain the generated rule name.");

        // --- 2. WHEN Clause (Conditions) ---
        // transactionAmount (DECIMAL) does NOT need quotes.
        // customerSegment (STRING) DOES need quotes.

        String expectedCondition1 = "transactionAmount > 50000.00 AND"; // Numeric comparison
        String expectedCondition2 = "customerSegment in ( \"PREMIUM\", \"BUSINESS\" )"; // String IN list

        assertTrue(drlContent.contains(expectedCondition1), "DRL should contain the transactionAmount condition.");
        assertTrue(drlContent.contains(expectedCondition2), "DRL should contain the customerSegment IN condition with quotes.");

        // --- 3. THEN Clause (Action) ---
        assertTrue(drlContent.contains("        $input.setPriceAmount(new BigDecimal(\"99.99\"));"), "DRL should set the price amount.");
        assertTrue(drlContent.contains("        $input.setCurrency(\"EUR\");"), "DRL should set the currency.");
        assertTrue(drlContent.contains("        update($input);"), "DRL should contain the update statement.");
    }
}