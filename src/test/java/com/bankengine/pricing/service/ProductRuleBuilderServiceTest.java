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

public class ProductRuleBuilderServiceTest extends BaseServiceTest {

    @Mock private PricingComponentRepository componentRepository;
    @Mock private PricingInputMetadataService metadataService;
    @Mock private DroolsExpressionBuilder droolsExpressionBuilder;

    @InjectMocks private ProductRuleBuilderService productRuleBuilderService;

    private static final String MOCKED_DRL_EXPRESSION =
        "((java.math.BigDecimal) customAttributes[\"transactionAmount\"]).compareTo(new java.math.BigDecimal(\"500.00\")) > 0";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Should generate DRL using activePricingTierIds and correct BigDecimal syntax")
    void testBuildRules_shouldGenerateCorrectDRL_withCustomAttributeAndFreeCount() {
        // ARRANGE
        PricingInputMetadata metadata = mock(PricingInputMetadata.class);
        when(metadata.getFqnType()).thenReturn("java.math.BigDecimal");

        // Tier 1: ABSOLUTE
        PriceValue priceValue1 = new PriceValue();
        priceValue1.setRawValue(new BigDecimal("10.00"));
        priceValue1.setValueType(ValueType.FEE_ABSOLUTE);

        PricingTier tier1 = mock(PricingTier.class);
        when(tier1.getId()).thenReturn(200L);
        when(tier1.getConditions()).thenReturn(Set.of(mock(TierCondition.class)));
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
        when(metadataService.getMetadataEntityByKey(any())).thenReturn(metadata);
        when(droolsExpressionBuilder.buildExpression(any(), any())).thenReturn(MOCKED_DRL_EXPRESSION);

        // ACT
        String drl = productRuleBuilderService.buildAllRulesForCompilation();

        // ASSERT
        // 1. Verify Date-Aware Filtering (The previous cause of failure)
        assertTrue(drl.contains("activePricingTierIds contains 200L"), "LHS must contain Tier 200 filter");
        assertTrue(drl.contains("activePricingTierIds contains 500L"), "LHS must contain Tier 500 filter");

        // 2. Verify Relational Expression
        assertTrue(drl.contains(MOCKED_DRL_EXPRESSION), "DRL should contain the compareTo expression for BigDecimal");

        // 3. Verify RHS
        assertTrue(drl.contains("priceValueFact.setMatchedTierId(200L);"));
        assertTrue(drl.contains("priceValueFact.setMatchedTierId(500L);"));
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
        assertTrue(drl.contains("activePricingTierIds contains 100L"), "Should verify Tier ID is present in LHS");
        assertTrue(drl.contains("priceValueFact.setMatchedTierId(100L);"), "Should verify Tier ID is passed to RHS fact");
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
    void buildRules_shouldReturnPlaceholderRulesWhenNoComponentsExist() {
        when(componentRepository.findAll()).thenReturn(Collections.emptyList());

        String drl = productRuleBuilderService.buildAllRulesForCompilation();

        assertTrue(drl.contains("rule \"Placeholder_pricing\""));
        assertTrue(drl.contains("$input : PricingInput ( )"));
    }

    @Test
    @DisplayName("Should handle multiple components using Tier IDs, not Component IDs")
    void testBuildRules_shouldHandleMultipleComponentsWithDistinctTargetIds() {
        // 1. Component A: Monthly Service Fee (ID 101)
        PricingComponent compA = mock(PricingComponent.class);
        when(compA.getId()).thenReturn(101L);
        when(compA.getName()).thenReturn("Service Fee");
        PricingTier tierA = mock(PricingTier.class);
        when(tierA.getId()).thenReturn(1001L);
        when(tierA.getPriceValues()).thenReturn(Set.of(new PriceValue()));
        when(compA.getPricingTiers()).thenReturn(List.of(tierA));

        // 2. Component B: Transaction Tax (ID 102)
        PricingComponent compB = mock(PricingComponent.class);
        when(compB.getId()).thenReturn(102L);
        when(compB.getName()).thenReturn("Tax");
        PricingTier tierB = mock(PricingTier.class);
        when(tierB.getId()).thenReturn(2002L);
        when(tierB.getPriceValues()).thenReturn(Set.of(new PriceValue()));
        when(compB.getPricingTiers()).thenReturn(List.of(tierB));

        when(componentRepository.findAll()).thenReturn(List.of(compA, compB));

        // ACT
        String drl = productRuleBuilderService.buildAllRulesForCompilation();

        // ASSERT
        // Ensure the "contains" logic points to Tiers (1001, 2002), NOT Components (101, 102)
        assertTrue(drl.contains("activePricingTierIds contains 1001L"), "DRL must target specific Tier ID 1001");
        assertTrue(drl.contains("activePricingTierIds contains 2002L"), "DRL must target specific Tier ID 2002");
    }
}