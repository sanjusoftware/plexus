package com.bankengine.pricing.service;

import com.bankengine.pricing.model.*;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.service.drl.DroolsExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service responsible for converting Product PricingComponent configuration (Tiers, Conditions, Values)
 * into a Drools Rule Language (DRL) string for runtime evaluation.
 */
@Service
public class ProductRuleBuilderService extends AbstractRuleBuilderService {

    public ProductRuleBuilderService(
            PricingComponentRepository componentRepository,
            PricingInputMetadataService metadataService,
            DroolsExpressionBuilder droolsExpressionBuilder) {
        super(componentRepository, metadataService, droolsExpressionBuilder);
    }

    private String getDrlHeader() {
        return """
            package bankengine.pricing.rules;
            
            import com.bankengine.rules.model.PricingInput;
            import com.bankengine.pricing.model.PriceValue;
            import java.math.BigDecimal;
            import java.util.Map;
            
            """;
    }

    @Transactional(readOnly = true)
    @Override
    public String buildAllRulesForCompilation() {
        StringBuilder finalDrl = new StringBuilder();
        finalDrl.append(getDrlHeader());
        List<PricingComponent> components = componentRepository.findAllEagerlyForRules();

        for (PricingComponent component : components) {
            finalDrl.append(generateComponentRulesBody(component));
            finalDrl.append("\n\n// --- End Component: ").append(component.getName()).append(" ---\n\n");
        }

        if (components.isEmpty()) {
             return buildPlaceholderRules();
        }

        return finalDrl.toString();
    }

    private String generateComponentRulesBody(PricingComponent component) {
        return component.getPricingTiers().stream()
                .map(tier -> buildSingleTierRule(component, tier))
                .collect(Collectors.joining("\n\n"));
    }

    private String buildSingleTierRule(PricingComponent component, PricingTier tier) {
        StringBuilder rule = new StringBuilder();

        String ruleName = String.format("Rule_%s_Tier_%d",
                component.getName().replaceAll("\\s", "_"),
                tier.getId());

        rule.append("rule \"").append(ruleName).append("\"\n");
        rule.append("    no-loop true\n");
        rule.append("    salience ").append(tier.getId()).append("\n");
        rule.append("    when\n");
        rule.append(buildLHSCondition(tier));
        rule.append("    then\n");
        rule.append(buildRHSAction(component, tier));
        rule.append("end\n");
        return rule.toString();
    }

    private String buildLHSCondition(PricingTier tier) {
        StringBuilder lhs = new StringBuilder();
        Set<TierCondition> conditions = tier.getConditions();

        if (!conditions.isEmpty()) {
            StringBuilder conditionBuilder = new StringBuilder();
            Iterator<TierCondition> iterator = conditions.iterator();

            while (iterator.hasNext()) {
                TierCondition condition = iterator.next();

                // 1. Resolve Metadata
                PricingInputMetadata metadata = getFactAttributeMetadata(condition.getAttributeName());

                // 2. Get the expression part using the injected builder
                String expression = droolsExpressionBuilder.buildExpression(condition, metadata);

                // Append the current condition expression
                conditionBuilder.append(expression);

                // 3. Append the connector
                if (iterator.hasNext()) {
                    String connector = condition.getConnector() != null ? condition.getConnector().name() : "AND";
                    conditionBuilder.append(" ").append(connector).append(" ");
                }
            }

            // Append the fact declaration and conditions
            String fullCondition = conditionBuilder.toString().trim();
            lhs.append("        $input : PricingInput ( ").append(fullCondition).append(" )\n");
        } else {
            // Rule fires unconditionally if no conditions are defined
            lhs.append("        $input : PricingInput ( true )\n");
        }

        return lhs.toString();
    }

    /**
     * Updated RHS action builder to insert a new PriceValue fact
     * instead of updating the PricingInput fact.
     */
    private String buildRHSAction(PricingComponent component, PricingTier tier) {
        StringBuilder rhs = new StringBuilder();

        if (tier.getPriceValues() != null && !tier.getPriceValues().isEmpty()) {
            PriceValue priceValue = tier.getPriceValues().iterator().next();
            String priceAmount = priceValue.getPriceAmount().toPlainString();
            String valueType = priceValue.getValueType().name();
            String componentCode = component.getName().replaceAll("\\s", "_");

            // --- New Logic: Create and Insert a PriceValue object ---
            rhs.append("        PriceValue priceValueFact = new PriceValue();\n");
            rhs.append(String.format("        priceValueFact.setMatchedTierId(Long.valueOf(%dL));\n", tier.getId()));
            rhs.append(String.format("        priceValueFact.setPriceAmount(new BigDecimal(\"%s\"));\n", priceAmount));
            rhs.append(String.format("        priceValueFact.setValueType(PriceValue.ValueType.valueOf(\"%s\"));\n", valueType));
            rhs.append(String.format("        priceValueFact.setComponentCode(\"%s\");\n", componentCode));
            rhs.append("        insert(priceValueFact);\n");
            // --- End New Logic ---

        } else {
            rhs.append("// No price actions defined for this tier.\n");
        }

        return rhs.toString();
    }

    public String buildPlaceholderRules() {
        return getDrlHeader() + """
            rule "PlaceholderRule_DoNothing"
                when
                    $input : PricingInput ( )
                then
                    // Do nothing, just ensure compilation succeeds
            end
            """;
    }
}
