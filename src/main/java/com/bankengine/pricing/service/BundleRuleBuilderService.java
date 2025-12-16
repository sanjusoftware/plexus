package com.bankengine.pricing.service;

import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.service.drl.DroolsExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service responsible for converting Bundle-level PricingComponent configuration
 * (WAIVER/DISCOUNT Tiers) into a Drools Rule Language (DRL) string for runtime evaluation.
 */
@Service
public class BundleRuleBuilderService extends AbstractRuleBuilderService {

    public BundleRuleBuilderService(
            PricingComponentRepository componentRepository,
            PricingInputMetadataService metadataService,
            DroolsExpressionBuilder droolsExpressionBuilder) {
        super(componentRepository, metadataService, droolsExpressionBuilder);
    }

    private static final String BUNDLE_INPUT_FACT = "com.bankengine.rules.model.BundlePricingInput";

    private String getDrlHeader() {
        return """
            package bankengine.bundle.rules;
            
            import %s;
            import com.bankengine.pricing.model.PriceValue;
            import java.math.BigDecimal;
            
            """.formatted(BUNDLE_INPUT_FACT);
    }

    @Transactional(readOnly = true)
    @Override
    public String buildAllRulesForCompilation() {
        StringBuilder finalDrl = new StringBuilder();
        finalDrl.append(getDrlHeader());

        List<PricingComponent> components = componentRepository.findByTypeIn(
                List.of(PricingComponent.ComponentType.WAIVER, PricingComponent.ComponentType.DISCOUNT)
        );

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
        rule.append("    no-loop true\n");
        rule.append("    salience ").append(tier.getId()).append("\n");
        rule.append("    when\n");
        rule.append(buildLHSCondition(tier));
        rule.append("    then\n");
        rule.append(buildRHSAction(tier, component.getName()));
        rule.append("end\n");
        return rule.toString();
    }

    private String buildLHSCondition(PricingTier tier) {
        StringBuilder lhs = new StringBuilder();

        String fullCondition = tier.getConditions().stream()
                .map(condition -> {
                    return droolsExpressionBuilder.buildExpression(
                            condition,
                            getFactAttributeMetadata(condition.getAttributeName())
                    );
                })
                .collect(Collectors.joining(" AND "));

        lhs.append(String.format("        $input : %s ( bankId != null, bankId == $input.getBankId()%s%s )\n",
                BUNDLE_INPUT_FACT.substring(BUNDLE_INPUT_FACT.lastIndexOf(".") + 1),
                fullCondition.isEmpty() ? "" : ", ",
                fullCondition));

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
        return getDrlHeader() + """
            rule "PlaceholderRule_Bundle_DoNothing"
                when
                    $input : BundlePricingInput ( )
                then
                    // Do nothing, just ensure compilation succeeds
            end
            """;
    }
}