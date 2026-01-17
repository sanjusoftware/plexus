package com.bankengine.pricing.service;

import com.bankengine.pricing.model.*;
import com.bankengine.pricing.model.PriceValue.ValueType;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.service.drl.DroolsExpressionBuilder;
import com.bankengine.test.config.BaseServiceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ProductRuleBuilderService focusing on DRL syntax generation.
 */
public class ProductRuleBuilderServiceTest extends BaseServiceTest {

    @Mock
    private PricingComponentRepository componentRepository;
    @Mock
    private PricingInputMetadataService metadataService;
    @Mock
    private DroolsExpressionBuilder droolsExpressionBuilder;

    @InjectMocks
    private ProductRuleBuilderService productRuleBuilderService;

    private static final String MOCKED_DRL_EXPRESSION = "((java.math.BigDecimal) customAttributes[\"transactionAmount\"]) > 500";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testBuildRules_shouldGenerateCorrectDRL() {
        // ARRANGE
        PricingInputMetadata metadata = mock(PricingInputMetadata.class);
        when(metadata.getFqnType()).thenReturn("java.math.BigDecimal");

        PriceValue priceValue = new PriceValue();
        priceValue.setRawValue(new BigDecimal("10.00"));
        priceValue.setValueType(ValueType.FEE_ABSOLUTE);

        TierCondition tierConditionMock = mock(TierCondition.class);
        when(tierConditionMock.getAttributeName()).thenReturn("transactionAmount");
        when(tierConditionMock.getConnector()).thenReturn(null);

        PricingTier tier = mock(PricingTier.class);
        when(tier.getId()).thenReturn(100L);
        when(tier.getConditions()).thenReturn(Set.of(tierConditionMock));
        when(tier.getPriceValues()).thenReturn(Set.of(priceValue));

        PricingComponent component = mock(PricingComponent.class);
        when(component.getId()).thenReturn(100L);
        when(component.getName()).thenReturn("TestFee");
        when(component.getPricingTiers()).thenReturn(List.of(tier));

        when(componentRepository.findAll()).thenReturn(List.of(component));
        when(metadataService.getMetadataEntityByKey("transactionAmount")).thenReturn(metadata);
        when(droolsExpressionBuilder.buildExpression(any(TierCondition.class), any(PricingInputMetadata.class)))
                .thenReturn(MOCKED_DRL_EXPRESSION);

        // ACT
        String drl = productRuleBuilderService.buildAllRulesForCompilation();

        // ASSERT
        assertTrue(drl.contains("rule \"PRICING_" + TEST_BANK_ID.toUpperCase()), "Rule name missing Bank ID");
        assertTrue(drl.contains("PricingInput ( bankId == \"" + TEST_BANK_ID + "\""), "LHS missing Bank ID");
        assertTrue(drl.contains(MOCKED_DRL_EXPRESSION), "LHS missing expression");

        // RHS Assertions (Matching actual template)
        assertTrue(drl.contains("PriceValue priceValueFact = new PriceValue();"));
        assertTrue(drl.contains("priceValueFact.setRawValue(new BigDecimal(\"10.00\"));"));
        assertTrue(drl.contains("priceValueFact.setBankId(\"" + TEST_BANK_ID + "\");"));
        assertTrue(drl.contains("insert(priceValueFact);"));
    }

    @Test
    void testBuildRules_shouldGenerateCorrectDRL_withCustomAttributeAndFreeCount() {
        // ARRANGE
        PricingInputMetadata metadata = mock(PricingInputMetadata.class);
        when(metadata.getFqnType()).thenReturn("java.math.BigDecimal");

        // Tier 1: ABSOLUTE
        PriceValue priceValue1 = new PriceValue();
        priceValue1.setRawValue(new BigDecimal("10.00"));
        priceValue1.setValueType(ValueType.FEE_ABSOLUTE);

        TierCondition tierConditionMock = mock(TierCondition.class);
        when(tierConditionMock.getAttributeName()).thenReturn("transactionAmount");

        PricingTier tier1 = mock(PricingTier.class);
        when(tier1.getId()).thenReturn(200L);
        when(tier1.getConditions()).thenReturn(Set.of(tierConditionMock));
        when(tier1.getPriceValues()).thenReturn(Set.of(priceValue1));

        // Tier 2: FREE_COUNT
        PriceValue priceValue2 = new PriceValue();
        priceValue2.setRawValue(new BigDecimal("5"));
        priceValue2.setValueType(ValueType.FREE_COUNT);

        PricingTier tier2 = mock(PricingTier.class);
        when(tier2.getId()).thenReturn(500L);
        when(tier2.getConditions()).thenReturn(Collections.emptySet());
        when(tier2.getPriceValues()).thenReturn(Set.of(priceValue2));

        PricingComponent component = mock(PricingComponent.class);
        when(component.getId()).thenReturn(101L);
        when(component.getName()).thenReturn("TestFee");
        when(component.getPricingTiers()).thenReturn(List.of(tier1, tier2));

        when(componentRepository.findAll()).thenReturn(List.of(component));
        when(metadataService.getMetadataEntityByKey("transactionAmount")).thenReturn(metadata);
        when(droolsExpressionBuilder.buildExpression(any(TierCondition.class), any(PricingInputMetadata.class)))
                .thenReturn(MOCKED_DRL_EXPRESSION);

        // ACT
        String drl = productRuleBuilderService.buildAllRulesForCompilation();

        // ASSERT
        // Rule Name Check
        assertTrue(drl.contains("PRICING_" + TEST_BANK_ID.toUpperCase() + "_TestFee_Tier_200"));
        assertTrue(drl.contains("PRICING_" + TEST_BANK_ID.toUpperCase() + "_TestFee_Tier_500"));

        // LHS Check (BankId must be present even in unconditional rules)
        assertTrue(drl.contains("PricingInput ( bankId == \"" + TEST_BANK_ID + "\""),
                "Unconditional rule should still check bankId");

        assertTrue(drl.contains("targetPricingComponentIds contains 101L"));

        // RHS ValueType Check (Direct Enum access, not valueOf)
        assertTrue(drl.contains("priceValueFact.setValueType(PriceValue.ValueType.FEE_ABSOLUTE);"));
        assertTrue(drl.contains("priceValueFact.setValueType(PriceValue.ValueType.FREE_COUNT);"));

        // RHS Amount Checks
        assertTrue(drl.contains("priceValueFact.setRawValue(new BigDecimal(\"10.00\"));"));
        assertTrue(drl.contains("priceValueFact.setRawValue(new BigDecimal(\"5\"));"));

        // Common RHS elements
        assertTrue(drl.contains("priceValueFact.setBankId(\"" + TEST_BANK_ID + "\");"));
        assertTrue(drl.contains("insert(priceValueFact);"));
    }

    @Test
    void buildRules_shouldReturnPlaceholderRulesWhenNoComponentsExist() {
        when(componentRepository.findAll()).thenReturn(Collections.emptyList());

        String drl = productRuleBuilderService.buildAllRulesForCompilation();

        assertTrue(drl.contains("rule \"Placeholder_pricing\""));
        assertTrue(drl.contains("$input : PricingInput ( )"));
    }

    @Test
    @DisplayName("Should generate DRL with distinct target IDs for multiple components")
    void testBuildRules_shouldHandleMultipleComponentsWithDistinctTargetIds() {
        // 1. Component A: Monthly Service Fee (ID 101)
        PricingComponent compA = mock(PricingComponent.class);
        when(compA.getId()).thenReturn(101L);
        when(compA.getName()).thenReturn("Service Fee");

        PricingTier tierA = mock(PricingTier.class);
        when(tierA.getId()).thenReturn(1001L);
        PriceValue pvA = new PriceValue();
        pvA.setRawValue(new BigDecimal("15.00"));
        pvA.setValueType(ValueType.FEE_ABSOLUTE);
        when(tierA.getPriceValues()).thenReturn(Set.of(pvA));
        when(compA.getPricingTiers()).thenReturn(List.of(tierA));

        // 2. Component B: Transaction Tax (ID 102)
        PricingComponent compB = mock(PricingComponent.class);
        when(compB.getId()).thenReturn(102L);
        when(compB.getName()).thenReturn("Tax");

        PricingTier tierB = mock(PricingTier.class);
        when(tierB.getId()).thenReturn(2002L);
        PriceValue pvB = new PriceValue();
        pvB.setRawValue(new BigDecimal("0.50"));
        pvB.setValueType(ValueType.FEE_PERCENTAGE);
        when(tierB.getPriceValues()).thenReturn(Set.of(pvB));
        when(compB.getPricingTiers()).thenReturn(List.of(tierB));

        // Stub repository to return both
        when(componentRepository.findAll()).thenReturn(List.of(compA, compB));

        // ACT
        String drl = productRuleBuilderService.buildAllRulesForCompilation();

        // ASSERT
        // Check that both rules exist
        assertTrue(drl.contains("rule \"PRICING_" + TEST_BANK_ID.toUpperCase() + "_Service_Fee_Tier_1001\""));
        assertTrue(drl.contains("rule \"PRICING_" + TEST_BANK_ID.toUpperCase() + "_Tax_Tier_2002\""));

        // Check that the "contains" logic points to the correct distinct IDs
        assertTrue(drl.contains("targetPricingComponentIds contains 101L"), "DRL should target Service Fee ID");
        assertTrue(drl.contains("targetPricingComponentIds contains 102L"), "DRL should target Tax ID");

        // Check that RHS actions use correct component codes
        assertTrue(drl.contains("priceValueFact.setComponentCode(\"Service_Fee\")"));
        assertTrue(drl.contains("priceValueFact.setComponentCode(\"Tax\")"));
    }

}