package com.bankengine.pricing.service.drl;

import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.TierCondition;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.rules.service.KieContainerReloadService;
import com.bankengine.test.config.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class DroolsValidationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private KieContainerReloadService reloadService;
    @Autowired private PricingComponentRepository componentRepository;

    @Test
    @Transactional
    @DisplayName("Critical: Ensure generated DRL compiles with LocalDate and Decimal comparisons")
    void shouldCompileComplexDrlWithoutErrors() {
        // Arrange: Create a component with a Date-based rule and a BigDecimal rule
        PricingComponent comp = new PricingComponent("ComplexComp", PricingComponent.ComponentType.FEE);
        PricingTier tier = new PricingTier(comp, "Complex Tier", BigDecimal.ZERO, null);

        // Rule 1: BigDecimal comparison (Uses compareTo)
        TierCondition cond1 = new TierCondition();
        cond1.setPricingTier(tier);
        cond1.setAttributeName("transactionAmount");
        cond1.setOperator(TierCondition.Operator.GT);
        cond1.setAttributeValue("1000.00");
        tier.getConditions().add(cond1);

        // Rule 2: Date comparison (Requires java.time.LocalDate import)
        TierCondition cond2 = new TierCondition();
        cond2.setPricingTier(tier);
        cond2.setAttributeName("effectiveDate"); // Assumes this exists in metadata
        cond2.setOperator(TierCondition.Operator.GE);
        cond2.setAttributeValue("2026-01-01");
        tier.getConditions().add(cond2);

        PriceValue val = new PriceValue(tier, new BigDecimal("50.00"), PriceValue.ValueType.FEE_ABSOLUTE);
        tier.getPriceValues().add(val);

        componentRepository.save(comp);

        // Act & Assert: This will throw a RuntimeException if DRL compilation fails
        assertDoesNotThrow(() -> {
            reloadService.reloadKieContainer(TEST_BANK_ID);
        }, "KieContainer reload failed! This usually means the generated DRL has syntax errors or missing imports (e.g. LocalDate).");
    }
}
