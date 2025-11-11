package com.bankengine.pricing.service;

import com.bankengine.pricing.model.*;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.repository.PricingInputMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DroolsRuleBuilderServiceTest {

    @Mock
    private PricingComponentRepository componentRepository;

    @Mock
    private PricingInputMetadataRepository metadataRepository;

    @InjectMocks
    private DroolsRuleBuilderService ruleBuilderService;

    // Mock Metadata Objects
    private final PricingInputMetadata amountMetadata = new PricingInputMetadata();
    private final PricingInputMetadata segmentMetadata = new PricingInputMetadata();

    @BeforeEach
    void setup() {
        // Setup DECIMAL metadata
        amountMetadata.setAttributeKey("transactionAmount");
        amountMetadata.setDataType("DECIMAL");

        // Setup STRING metadata
        segmentMetadata.setAttributeKey("customerSegment");
        segmentMetadata.setDataType("STRING");

        // We ensure the cache lookup can resolve these types
        when(metadataRepository.findByAttributeKeyIn(anySet()))
            .thenReturn(List.of(amountMetadata, segmentMetadata));
    }

    private PricingComponent createMockComponentWithTier() {
        PricingComponent component = new PricingComponent();
        component.setId(1L);
        component.setName("MonthlyFeeComponent");

        PricingTier tier = new PricingTier();
        tier.setId(10L);

        // 1. DECIMAL Condition: transactionAmount > 500
        TierCondition condition1 = new TierCondition();
        condition1.setAttributeName("transactionAmount");
        condition1.setOperator(TierCondition.Operator.GT);
        condition1.setAttributeValue("500.00");
        condition1.setConnector(TierCondition.LogicalConnector.AND);

        // 2. STRING Condition: customerSegment == "PREMIUM"
        TierCondition condition2 = new TierCondition();
        condition2.setAttributeName("customerSegment");
        condition2.setOperator(TierCondition.Operator.EQ);
        condition2.setAttributeValue("PREMIUM");

        tier.setConditions(Set.of(condition1, condition2));

        // Setup PriceValue (RHS)
        PriceValue value = new PriceValue();
        value.setPriceAmount(new BigDecimal("10.00"));
        value.setValueType(PriceValue.ValueType.ABSOLUTE);
        value.setCurrency("USD");
        tier.setPriceValues(Set.of(value));

        component.setPricingTiers(List.of(tier));
        return component;
    }

    @Test
    void buildRules_shouldGenerateDrlWithCorrectMapAccessAndCasting() {
        // Arrange
        PricingComponent component = createMockComponentWithTier();
        when(componentRepository.findAllEagerlyForRules()).thenReturn(List.of(component));

        // Act
        String drl = ruleBuilderService.buildAllRulesForCompilation();

        // Assert
        // 1. Check for Map import
        assertThat(drl).contains("import java.util.Map;");

        // 2. Check DECIMAL condition (Requires casting and direct map access)
        String expectedDecimalCondition = "((java.math.BigDecimal) customAttributes[\"transactionAmount\"]) > 500.00";
        assertThat(drl).contains(expectedDecimalCondition);

        // 3. Check STRING condition (Requires quotes around value, no casting)
        String expectedStringCondition = "customAttributes[\"customerSegment\"] == \"PREMIUM\"";
        assertThat(drl).contains(expectedStringCondition);

        // 4. Check that the final DRL structure is correct
        assertThat(drl).contains("rule \"Rule_MonthlyFeeComponent_Tier_10\"");
        assertThat(drl).contains("$input : PricingInput (");
        assertThat(drl).contains("then");
    }
}