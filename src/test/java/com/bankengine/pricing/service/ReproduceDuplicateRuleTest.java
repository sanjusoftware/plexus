package com.bankengine.pricing.service;

import com.bankengine.pricing.model.*;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.service.drl.DroolsExpressionBuilder;
import com.bankengine.test.config.BaseServiceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class ReproduceDuplicateRuleTest extends BaseServiceTest {

    @Mock private PricingComponentRepository pricingComponentRepository;
    @Mock private PricingInputMetadataService metadataService;
    @Mock private DroolsExpressionBuilder droolsExpressionBuilder;

    @InjectMocks private ProductRuleBuilderService productRuleBuilderService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testBuildRules_shouldNotGenerateDuplicateRulesWhenRepositoryReturnsDuplicates() {
        // ARRANGE
        PriceValue priceValue = new PriceValue();
        priceValue.setRawValue(new BigDecimal("10.00"));
        priceValue.setValueType(PriceValue.ValueType.FEE_ABSOLUTE);

        PricingTier tier = PricingTier.builder()
                .id(3L)
                .code("STD_TIER")
                .name("Standard Tier")
                .priceValues(Set.of(priceValue))
                .build();

        PricingComponent component = PricingComponent.builder()
                .id(1L)
                .code("MONTHLY_MAINTENANCE_FEE")
                .version(1)
                .name("Monthly Maintenance Fee")
                .pricingTiers(List.of(tier))
                .build();

        // Mimic the duplication that likely happens in the repository
        when(pricingComponentRepository.findAllWithDetailsBy()).thenReturn(List.of(component, component));

        // ACT
        String drl = productRuleBuilderService.buildAllRulesForCompilation();
        System.out.println("DEBUG: Generated DRL:\n" + drl);

        // ASSERT
        // Count how many times the rule name appears
        int count = 0;
        Pattern p = Pattern.compile("rule \"PRICING_TEST_BANK_MONTHLY_MAINTENANCE_FEE_V1_Tier_STD_TIER\"");
        Matcher m = p.matcher(drl);
        while (m.find()) {
            count++;
        }

        assertEquals(1, count, "The rule should only be generated once even if the component is duplicated in the list");
    }
}
