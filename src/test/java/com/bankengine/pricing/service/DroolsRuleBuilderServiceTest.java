package com.bankengine.pricing.service;

import com.bankengine.pricing.model.*;
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

    @BeforeEach
    void setUp() {
        // Initializes all fields annotated with @Mock and @InjectMocks
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void buildRules_shouldGenerateDrlWithCorrectMapAccessAndCasting() {
        // ARRANGE

        // 1. Setup the input data (Mocks for component, tier, condition, and metadata)
        PricingComponent component = mock(PricingComponent.class);
        PricingTier tier = mock(PricingTier.class);
        TierCondition condition = mock(TierCondition.class);
        PricingInputMetadata metadata = new PricingInputMetadata();
        metadata.setAttributeKey("transactionAmount");
        metadata.setDataType("DECIMAL");

        PriceValue priceValue = new PriceValue();
        priceValue.setPriceAmount(new BigDecimal("10.00"));
        priceValue.setValueType(PriceValue.ValueType.ABSOLUTE);
        priceValue.setCurrency("USD");

        // 2. Define the behavior of the mocked objects
        when(componentRepository.findAllEagerlyForRules()).thenReturn(List.of(component));
        when(component.getPricingTiers()).thenReturn(List.of(tier));
        when(component.getName()).thenReturn("TestFee");
        when(component.getId()).thenReturn(1L);

        when(tier.getConditions()).thenReturn(Set.of(condition));
        when(tier.getPriceValues()).thenReturn(Set.of(priceValue));
        when(tier.getId()).thenReturn(100L);

        when(condition.getAttributeName()).thenReturn("transactionAmount");

        // Mock the metadata repository lookup and caching logic
        when(metadataService.getMetadataEntitiesByKeys(anySet())).thenReturn(List.of(metadata));
        when(metadataService.getMetadataEntityByKey("transactionAmount")).thenReturn(metadata);

        // 3. CRITICAL: Stub the new DroolsExpressionBuilder
        // When the service calls the builder, the builder should return the DRL fragment
        when(droolsExpressionBuilder.buildExpression(any(TierCondition.class), any(PricingInputMetadata.class)))
            .thenReturn("((java.math.BigDecimal) customAttributes[\"transactionAmount\"]) > 500");

        // ACT
        String drl = droolsRuleBuilderService.buildAllRulesForCompilation();

        // ASSERT
        // The DRL string should now contain the expected fragment, proving the integration works
        assertTrue(drl.contains("Rule_TestFee_Tier_100"), "DRL must contain the rule header.");
        assertTrue(drl.contains("((java.math.BigDecimal) customAttributes[\"transactionAmount\"]) > 500"), "DRL must contain the expression from the builder.");
        assertTrue(drl.contains("setPriceAmount(new BigDecimal(\"10.00\"))"), "DRL must contain the RHS action.");
    }

    @Test
    void buildRules_shouldReturnPlaceholderRulesWhenNoComponentsExist() {
        when(componentRepository.findAllEagerlyForRules()).thenReturn(Collections.emptyList());
        String drl = droolsRuleBuilderService.buildAllRulesForCompilation();
        assertTrue(drl.contains("PlaceholderRule_DoNothing"), "DRL should contain the placeholder rule.");
    }
}