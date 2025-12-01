package com.bankengine.pricing.service;

import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.TierCondition;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.service.drl.DroolsExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service responsible for converting Bundle-level PricingComponent configuration
 * (WAIVER/DISCOUNT Tiers) into a Drools Rule Language (DRL) string for runtime evaluation
 * by the BundleRulesEngineService.
 */
@Service
public class BundleDroolsRuleBuilderService {

    @Autowired
    private PricingComponentRepository componentRepository;

    @Autowired
    private DroolsExpressionBuilder droolsExpressionBuilder;

    // We assume the BundlePricingInput fact is used here
    private static final String BUNDLE_INPUT_FACT = "com.bankengine.rules.model.BundlePricingInput";

    private String getDrlHeader() {
        return """
            package bankengine.bundle.rules;
            
            import %s; // The bundle fact
            import java.math.BigDecimal;
            
            """.formatted(BUNDLE_INPUT_FACT);
    }

    /**
     * Builds DRL rules specifically for Bundle adjustments.
     * We look for PricingComponents of type WAIVER or DISCOUNT.
     */
    @Transactional(readOnly = true)
    public String buildAllBundleRulesForCompilation() {
        StringBuilder finalDrl = new StringBuilder();
        finalDrl.append(getDrlHeader());
        finalDrl.append(getDrlHeader());

        // We assume a custom method on the repository exists to fetch the correct components
        List<PricingComponent> components = componentRepository.findByTypeIn(
                List.of(PricingComponent.ComponentType.WAIVER, PricingComponent.ComponentType.DISCOUNT)
        );

        // NOTE: Unlike component pricing, bundle rules should apply to the bundle input fact.
        // The DRL will enforce multi-tenancy using the bankId fact.
        for (PricingComponent component : components) {
            finalDrl.append(generateBundleComponentRulesBody(component));
            finalDrl.append("\n\n// --- End Bundle Component: ").append(component.getName()).append(" ---\n\n");
        }

        if (components.isEmpty()) {
            return buildPlaceholderRules();
        }

        return finalDrl.toString();
    }

    private String generateBundleComponentRulesBody(PricingComponent component) {
        return component.getPricingTiers().stream()
                .map(tier -> buildSingleBundleTierRule(component, tier))
                .collect(Collectors.joining("\n\n"));
    }

    private String buildSingleBundleTierRule(PricingComponent component, PricingTier tier) {
        StringBuilder rule = new StringBuilder();

        String ruleName = String.format("BundleRule_%s_Tier_%d",
                component.getName().replaceAll("\\s", "_"),
                tier.getId());

        rule.append("rule \"").append(ruleName).append("\"\n");
        rule.append("    when\n");
        // Use the Tier Conditions (e.g., grossTotalAmount > 100)
        rule.append(buildLHSCondition(tier));
        rule.append("    then\n");
        // Apply adjustment logic
        rule.append(buildRHSAction(tier, component.getName()));
        rule.append("end\n");
        return rule.toString();
    }

    /**
     * The LHS condition logic is largely reusable, but targets the BundlePricingInput fact.
     * This requires DroolsExpressionBuilder to be able to access the new Bundle facts.
     */
    private String buildLHSCondition(PricingTier tier) {
        StringBuilder lhs = new StringBuilder();
        Set<TierCondition> conditions = tier.getConditions();

        // Logic to construct the condition string (relying on DroolsExpressionBuilder)
        String fullCondition = tier.getConditions().stream()
                // Assume droolsExpressionBuilder.buildExpression is enhanced to support BundlePricingInput fields
                // For now, we'll assume the conditions check fields that are available in BundlePricingInput
                .map(condition -> droolsExpressionBuilder.buildExpression(condition, /* Metadata lookup needed */ null))
                .collect(Collectors.joining(" AND "));


        // IMPORTANT: Add the multi-tenancy check directly in the DRL LHS
        // The BundlePricingInput must have a field named 'bankId'
        lhs.append(String.format("        $input : %s ( bankId != null, bankId == $input.getBankId(), %s )\n",
                BUNDLE_INPUT_FACT.substring(BUNDLE_INPUT_FACT.lastIndexOf(".") + 1), fullCondition));

        return lhs.toString();
    }

    private String buildRHSAction(PricingTier tier, String componentName) {
        StringBuilder rhs = new StringBuilder();

        // Bundle rules should apply an adjustment to an accumulated list
        if (tier.getPriceValues() != null && !tier.getPriceValues().isEmpty()) {
            PriceValue priceValue = tier.getPriceValues().iterator().next();
            String priceAmount = priceValue.getPriceAmount().toPlainString();
            String adjustmentType = priceValue.getValueType().name(); // Use type as adjustment key

            rhs.append(String.format("        $input.addAdjustment(\"%s_Tier%d\", new BigDecimal(\"%s\"), \"%s\");\n",
                    componentName.replaceAll("\\s", "_"), tier.getId(), priceAmount, adjustmentType));
            rhs.append("        update($input);\n");
        } else {
            rhs.append("// No adjustment action defined for this bundle tier.\n");
        }
        return rhs.toString();
    }

    public String buildPlaceholderRules() {
        return """
            package bankengine.bundle.rules;

            import com.bankengine.rules.model.BundlePricingInput;
            import java.math.BigDecimal;

            rule "PlaceholderRule_Bundle_DoNothing"
                when
                    $input : BundlePricingInput ( )
                then
                    // Do nothing, just ensure compilation succeeds
            end
            """;
    }
}