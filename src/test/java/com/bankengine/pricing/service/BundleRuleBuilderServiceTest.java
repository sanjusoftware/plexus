package com.bankengine.pricing.service;

import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BundleRuleBuilderServiceTest extends BaseServiceTest {

    @Mock private PricingComponentRepository componentRepository;
    @Mock private PricingInputMetadataService metadataService;
    @Mock private DroolsExpressionBuilder droolsExpressionBuilder;

    @InjectMocks private BundleRuleBuilderService bundleRuleBuilderService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Should filter for WAIVER/DISCOUNT and generate update($input) action")
    void testBuildRules_shouldGenerateBundleSpecificDRL() {
        // ARRANGE
        PricingComponent waiver = mock(PricingComponent.class);
        when(waiver.getName()).thenReturn("Staff Waiver");

        PricingTier tier = mock(PricingTier.class);
        when(tier.getId()).thenReturn(999L);

        PriceValue pv = new PriceValue();
        pv.setRawValue(new BigDecimal("100.00"));
        pv.setValueType(PriceValue.ValueType.DISCOUNT_PERCENTAGE);

        when(tier.getPriceValues()).thenReturn(Set.of(pv));
        when(waiver.getPricingTiers()).thenReturn(List.of(tier));

        // Mock repository to return only the allowed types
        when(componentRepository.findByTypeIn(any())).thenReturn(List.of(waiver));

        // ACT
        String drl = bundleRuleBuilderService.buildAllRulesForCompilation();

        // ASSERT
        // FIX: Match actual package prefix 'bankengine.bundle.rules' and the mock bank ID
        assertTrue(drl.contains("package bankengine.bundle.rules.testbank"), "Incorrect package path. Found: " + drl);
        assertTrue(drl.contains("import com.bankengine.rules.model.BundlePricingInput"), "Missing import");
        assertTrue(drl.contains("$input : BundlePricingInput"), "Incorrect fact type");

        // Verify RHS Action
        assertTrue(drl.contains("$input.addAdjustment(\"Staff_Waiver_Tier999\", new BigDecimal(\"100.00\"), \"DISCOUNT_PERCENTAGE\");"),
                "RHS should call addAdjustment with sanitized name");
        assertTrue(drl.contains("update($input);"), "Bundle rules must update the existing input fact");

        // Optional: Verify the debug print is present
        assertTrue(drl.contains("Rule Fired: BUNDLE_TEST_BANK_Staff_Waiver_Tier_999"));
    }

    @Test
    @DisplayName("Should return placeholder for bundle when no components found")
    void testBuildRules_noComponents() {
        when(componentRepository.findByTypeIn(any())).thenReturn(Collections.emptyList());

        String drl = bundleRuleBuilderService.buildAllRulesForCompilation();

        assertTrue(drl.contains("rule \"Placeholder_bundle\""));
        assertTrue(drl.contains("BundlePricingInput") && drl.contains("then"), "Placeholder must reference the correct Fact");
    }

    @Test
    @DisplayName("Should handle tiers with no price values gracefully")
    void testBuildRHSAction_noPriceValues() {
        // ARRANGE
        PricingComponent waiver = mock(PricingComponent.class);
        when(waiver.getName()).thenReturn("Empty Waiver");

        PricingTier tier = mock(PricingTier.class);
        when(tier.getId()).thenReturn(111L);
        // Explicitly set price values to empty
        when(tier.getPriceValues()).thenReturn(Collections.emptySet());

        when(waiver.getPricingTiers()).thenReturn(List.of(tier));
        when(componentRepository.findByTypeIn(any())).thenReturn(List.of(waiver));

        // ACT
        String drl = bundleRuleBuilderService.buildAllRulesForCompilation();

        // ASSERT
        assertTrue(drl.contains("// No adjustment defined"), "Should contain the empty adjustment comment");
        assertFalse(drl.contains("addAdjustment"), "Should not attempt to add an adjustment");
    }
}