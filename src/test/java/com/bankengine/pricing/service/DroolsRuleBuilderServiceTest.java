package com.bankengine.pricing.service;

import com.bankengine.pricing.model.*;
import com.bankengine.pricing.model.PriceValue.ValueType;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.service.drl.DroolsExpressionBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DroolsRuleBuilderService focusing on DRL syntax generation.
 */
public class DroolsRuleBuilderServiceTest {

    @Mock
    private PricingComponentRepository componentRepository;
    @Mock
    private PricingInputMetadataService metadataService;
    @Mock
    private DroolsExpressionBuilder droolsExpressionBuilder;

    @InjectMocks
    private DroolsRuleBuilderService droolsRuleBuilderService;

    private static final String MOCKED_DRL_EXPRESSION = "((java.math.BigDecimal) customAttributes[\"transactionAmount\"]) > 500";

    @BeforeEach
    void setUp() {
        // Initializes all fields annotated with @Mock and @InjectMocks
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testBuildRules_shouldGenerateCorrectDRL() {
        // ARRANGE: Setup Mocks for a single component with one tier
        // Component: Name='TestFee', Tier: ID=100, Condition: customAttribute > 500, PriceValue: ABSOLUTE 10.00
        PricingInputMetadata metadata = mock(PricingInputMetadata.class);
        when(metadata.getFqnType()).thenReturn("java.math.BigDecimal");

        PriceValue priceValue = new PriceValue();
        priceValue.setPriceAmount(new BigDecimal("10.00"));
        priceValue.setValueType(ValueType.ABSOLUTE);

        // Create a specific mock for TierCondition and stub its attributeName
        TierCondition tierConditionMock = mock(TierCondition.class);
        when(tierConditionMock.getAttributeName()).thenReturn("transactionAmount");
        when(tierConditionMock.getConnector()).thenReturn(null);

        PricingTier tier = mock(PricingTier.class);
        when(tier.getId()).thenReturn(100L);
        when(tier.getConditions()).thenReturn(Set.of(tierConditionMock));
        when(tier.getPriceValues()).thenReturn(Set.of(priceValue));

        PricingComponent component = mock(PricingComponent.class);
        when(component.getName()).thenReturn("TestFee");
        when(component.getPricingTiers()).thenReturn(List.of(tier));

        when(componentRepository.findAllEagerlyForRules()).thenReturn(List.of(component));

        // Metadata and Expression Stubbing
        when(metadataService.getMetadataEntitiesByKeys(anySet())).thenReturn(List.of(metadata));
        when(metadataService.getMetadataEntityByKey("transactionAmount")).thenReturn(metadata);

        when(droolsExpressionBuilder.buildExpression(any(TierCondition.class), any(PricingInputMetadata.class)))
            .thenReturn(MOCKED_DRL_EXPRESSION);

        // ACT
        String drl = droolsRuleBuilderService.buildAllRulesForCompilation();

        // ASSERT
        assertTrue(drl.contains("Rule_TestFee_Tier_100"), "DRL must contain the rule header.");
        // Assertion now relies on the fixed mock setup to find the string
        assertTrue(drl.contains(MOCKED_DRL_EXPRESSION), "DRL must contain the expression from the builder.");

        // Use a more precise check for the price amount action
        assertTrue(drl.contains("        $input.setPriceAmount(new BigDecimal(\"10.00\"));"), "DRL must contain the price setter RHS action.");
        assertTrue(drl.contains("        update($input);\n"), "DRL must contain the mandatory 'update($input);' action.");
    }

    @Test
    void testBuildRules_shouldGenerateCorrectDRL_withCustomAttributeAndFreeCount() {
        // ARRANGE: Setup Mocks for a component with two tiers:
        // Tier 1 (200): ABSOLUTE 10.00, Condition: customAttribute > 500
        // Tier 2 (500): FREE_COUNT 5, Condition: NONE

        PricingInputMetadata metadata = mock(PricingInputMetadata.class);
        when(metadata.getFqnType()).thenReturn("java.math.BigDecimal");

        // --- Tier 1: Custom Attribute (ABS) ---
        PriceValue priceValue1 = new PriceValue();
        priceValue1.setPriceAmount(new BigDecimal("10.00"));
        priceValue1.setValueType(ValueType.ABSOLUTE);

        // Create a specific mock for TierCondition and stub its attributeName
        TierCondition tierConditionMock = mock(TierCondition.class);
        when(tierConditionMock.getAttributeName()).thenReturn("transactionAmount");
        when(tierConditionMock.getConnector()).thenReturn(null);

        PricingTier tier1 = mock(PricingTier.class);
        when(tier1.getId()).thenReturn(200L);
        when(tier1.getConditions()).thenReturn(Set.of(tierConditionMock));
        when(tier1.getPriceValues()).thenReturn(Set.of(priceValue1));

        // --- Tier 2: Free Count (Uses FREE_COUNT ValueType) ---
        PriceValue priceValue2 = new PriceValue();
        priceValue2.setPriceAmount(new BigDecimal("5"));
        priceValue2.setValueType(ValueType.FREE_COUNT);

        PricingTier tier2 = mock(PricingTier.class);
        when(tier2.getId()).thenReturn(500L);
        when(tier2.getConditions()).thenReturn(Collections.emptySet()); // Unconditional rule
        when(tier2.getPriceValues()).thenReturn(Set.of(priceValue2));

        // --- Component ---
        PricingComponent component = mock(PricingComponent.class);
        when(component.getName()).thenReturn("TestFee");
        when(component.getPricingTiers()).thenReturn(List.of(tier1, tier2));

        when(componentRepository.findAllEagerlyForRules()).thenReturn(List.of(component));

        // Metadata and Expression Stubbing
        when(metadataService.getMetadataEntitiesByKeys(anySet())).thenReturn(List.of(metadata));
        when(metadataService.getMetadataEntityByKey("transactionAmount")).thenReturn(metadata);

        // Stub the expression builder to return the expected DRL fragment
        when(droolsExpressionBuilder.buildExpression(any(TierCondition.class), any(PricingInputMetadata.class)))
            .thenReturn(MOCKED_DRL_EXPRESSION);

        // ACT
        String drl = droolsRuleBuilderService.buildAllRulesForCompilation();

        // ASSERT
        // Rule 1: Custom Attribute (TestFee_Tier_200)
        assertTrue(drl.contains("Rule_TestFee_Tier_200"), "DRL must contain the descriptive rule header (Rule_<ComponentName>_Tier_<TierId>).");
        assertTrue(drl.contains(MOCKED_DRL_EXPRESSION),
                   "DRL must contain the expression from the builder for the custom attribute.");

        // Standard PriceValue Output (for Tier 200)
        assertTrue(drl.contains("        $input.setPriceAmount(new BigDecimal(\"10.00\"));"), "DRL must contain the price setter for the standard RHS action.");
        assertTrue(drl.contains("        $input.setValueType(\"ABSOLUTE\");"), "DRL must contain the ValueType RHS action.");

        // Rule 2: FREE_COUNT
        assertTrue(drl.contains("Rule_TestFee_Tier_500"), "DRL must contain the FREE_COUNT rule header (Rule_TestFee_Tier_500).");
        // Check for the unconditional rule trigger
        assertTrue(drl.contains("        $input : PricingInput ( true )\n"), "DRL must contain the unconditional rule trigger.");
        assertTrue(drl.contains("        $input.setPriceAmount(new BigDecimal(\"5\"));"), "DRL must contain the FREE_COUNT price amount (the count value).");
        assertTrue(drl.contains("        $input.setValueType(\"FREE_COUNT\");"), "DRL must contain the FREE_COUNT ValueType.");

        assertTrue(drl.contains("        update($input);\n"),
                   "DRL must contain the mandatory 'update($input);' action.");
    }


    @Test
    void buildRules_shouldReturnPlaceholderRulesWhenNoComponentsExist() {
        when(componentRepository.findAllEagerlyForRules()).thenReturn(Collections.emptyList());
        String drl = droolsRuleBuilderService.buildAllRulesForCompilation();
        assertTrue(drl.contains("PlaceholderRule_DoNothing"), "DRL should contain the placeholder rule when no components are found.");
    }
}