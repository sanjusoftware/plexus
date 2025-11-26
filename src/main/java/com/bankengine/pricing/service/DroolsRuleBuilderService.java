package com.bankengine.pricing.service;

import com.bankengine.pricing.model.*;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.service.drl.DroolsExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service responsible for converting PricingComponent configuration (Tiers, Conditions, Values)
 * into a Drools Rule Language (DRL) string for runtime evaluation.
 */
@Service
public class DroolsRuleBuilderService {

    @Autowired
    private PricingComponentRepository componentRepository;

    @Autowired
    private PricingInputMetadataService metadataService;

    @Autowired
    private DroolsExpressionBuilder droolsExpressionBuilder;

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
    public String buildAllRulesForCompilation() {
        StringBuilder finalDrl = new StringBuilder();
        finalDrl.append(getDrlHeader());
        List<PricingComponent> components = componentRepository.findAllEagerlyForRules();

        // --- 2. Pre-loading Metadata ---
        Set<String> allRequiredAttributes = components.stream()
                .flatMap(component -> component.getPricingTiers().stream())
                .flatMap(tier -> tier.getConditions().stream())
                .map(TierCondition::getAttributeName)
                .collect(Collectors.toSet());

        // This call prefetches all required metadata entities in one transactional query
        // and prime's the cache for subsequent single lookups in buildLHSCondition.
        metadataService.getMetadataEntitiesByKeys(allRequiredAttributes);


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
        rule.append("    when\n");
        rule.append(buildLHSCondition(tier));
        rule.append("    then\n");
        rule.append(buildRHSAction(tier));
        rule.append("end\n");
        return rule.toString();
    }


    /**
     * Uses the injected metadataService for resolution.
     */
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

    private PricingInputMetadata getFactAttributeMetadata(String attributeName) {
        // The service layer now handles the cache check and the database fallback logic.
        return metadataService.getMetadataEntityByKey(attributeName);
    }

    private String buildRHSAction(PricingTier tier) {
        StringBuilder rhs = new StringBuilder();

        if (tier.getPriceValues() != null && !tier.getPriceValues().isEmpty()) {
            PriceValue priceValue = tier.getPriceValues().iterator().next();
            String priceAmount = priceValue.getPriceAmount().toPlainString();
            String valueType = priceValue.getValueType().name();
            String currency = priceValue.getCurrency();

            rhs.append(String.format("        $input.setMatchedTierId(%dL);\n", tier.getId()));
            rhs.append(String.format("        $input.setPriceAmount(new BigDecimal(\"%s\"));\n", priceAmount));
            rhs.append(String.format("        $input.setValueType(\"%s\");\n", valueType));
            rhs.append(String.format("        $input.setCurrency(\"%s\");\n", currency));
            rhs.append("        $input.setRuleFired(true);\n");
            rhs.append("        update($input);\n");
        } else {
            rhs.append("// No price actions defined for this tier.\n");
        }

        return rhs.toString();
    }

    public String buildPlaceholderRules() {
        return """
            package bankengine.pricing.rules;

            import com.bankengine.rules.model.PricingInput;
            import com.bankengine.pricing.model.PriceValue;
            import java.math.BigDecimal;
            import java.util.Map;

            rule "PlaceholderRule_DoNothing"
                when
                    $input : PricingInput ( )
                then
                    // Do nothing, just ensure compilation succeeds
            end
            """;
    }
}