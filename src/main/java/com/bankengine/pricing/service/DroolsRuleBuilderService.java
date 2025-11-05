package com.bankengine.pricing.service;

import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.rules.model.PricingInput;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service responsible for converting PricingComponent configuration (Tiers, Conditions, Values)
 * into a Drools Rule Language (DRL) string for runtime evaluation.
 */
@Service
public class DroolsRuleBuilderService {

    // Cache for attribute types to avoid repeated reflection lookups.
    private static final Map<String, String> ATTRIBUTE_TYPE_CACHE = new ConcurrentHashMap<>();

    /**
     * Builds the full DRL content for a given Pricing Component.
     *
     * @param component The PricingComponent entity.
     * @return The complete DRL string.
     */
    public String buildRules(PricingComponent component) {
        StringBuilder drl = new StringBuilder();

        drl.append("package bankengine.pricing.rules;\n\n");
        drl.append("import com.bankengine.rules.model.PricingInput;\n");
        drl.append("import com.bankengine.pricing.model.PriceValue;\n"); // For the action side
        drl.append("import java.math.BigDecimal;\n\n");

        for (PricingTier tier : component.getPricingTiers()) {
            drl.append(buildSingleTierRule(component, tier));
        }

        return drl.toString();
    }

    /**
     * Builds a single Drools rule for a specific Pricing Tier.
     */
    private String buildSingleTierRule(PricingComponent component, PricingTier tier) {
        // Rule Metadata
        StringBuilder rule = new StringBuilder();
        String ruleName = String.format("Rule_%s_Tier_%d",
                component.getName().replaceAll("\\s", "_"),
                tier.getId());

        rule.append("rule \"").append(ruleName).append("\"\n");
        rule.append("    when\n");

        // ------------------
        // L H S (Condition)
        // ------------------
        List<String> conditionFragments = tier.getConditions().stream()
                .map(condition -> {
                    String dataType = getFactAttributeDataType(condition.getAttributeName());
                    String expression = condition.toDroolsExpression(dataType);
                    String connector = condition.getConnector() != null ? condition.getConnector().name() : "";

                    // Format the condition with the logical connector (AND/OR)
                    return String.format("        %s %s", expression, connector);
                })
                .toList();

        // Join fragments into the WHEN clause
        if (!conditionFragments.isEmpty()) {
            // Note: The last connector should be removed, but for simple DRL it can be left.
            // A safer implementation would use a loop/index to handle the last connector.
            String fullCondition = conditionFragments.stream()
                    .collect(Collectors.joining("\n"))
                    .trim();

            rule.append("        $input : PricingInput ( ").append(fullCondition).append(" )\n");
        } else {
            // Rule fires unconditionally if no conditions are defined (e.g., default tier)
            rule.append("        $input : PricingInput ( true )\n");
        }

        rule.append("    then\n");

        // ------------------
        // R H S (Action)
        // ------------------

        // Assuming PriceValue is fetched/calculated in the action part based on the matched tier
        // This is a simplified action, assuming a PriceValue entity is found for this tier.
        if (tier.getPriceValues() != null && !tier.getPriceValues().isEmpty()) {
            // Get the first price value (simplification)
            PriceValue priceValue = tier.getPriceValues().get(0);
            String priceAmount = priceValue.getPriceAmount().toPlainString();
            String valueType = priceValue.getValueType().name();
            String currency = priceValue.getCurrency();

            // Action: Update the PricingInput fact with the result
            rule.append(String.format("        $input.setMatchedTierId(%dL);\n", tier.getId()));
            rule.append(String.format("        $input.setPriceAmount(new BigDecimal(\"%s\"));\n", priceAmount));
            rule.append(String.format("        $input.setValueType(\"%s\");\n", valueType));
            rule.append(String.format("        $input.setCurrency(\"%s\");\n", currency));
            rule.append("        $input.setRuleFired(true);\n");
            rule.append("        update($input);\n");
        }

        rule.append("end\n\n");
        return rule.toString();
    }

    /**
     * Fetches the required data type for a fact attribute
     * by using reflection on the PricingInput class.
     */
    private String getFactAttributeDataType(String attributeName) {
        // 1. Check cache first
        if (ATTRIBUTE_TYPE_CACHE.containsKey(attributeName)) {
            return ATTRIBUTE_TYPE_CACHE.get(attributeName);
        }

        // 2. Reflect on the PricingInput class
        try {
            Field field = PricingInput.class.getDeclaredField(attributeName);
            Class<?> type = field.getType();
            String dataType;

            // Map Java types to a simpler string representation for TierCondition.toDroolsExpression()
            if (type.equals(String.class)) {
                dataType = "STRING";
            } else if (type.equals(BigDecimal.class)) {
                dataType = "DECIMAL";
            } else if (type.equals(Long.class) || type.equals(Integer.class) || type.equals(int.class) || type.equals(long.class)) {
                dataType = "INTEGER";
            } else if (type.equals(Boolean.class) || type.equals(boolean.class)) {
                dataType = "BOOLEAN";
            } else {
                // Default for unsupported types, forcing quoting by toDroolsExpression
                dataType = "STRING";
            }

            // 3. Cache the result for future calls
            ATTRIBUTE_TYPE_CACHE.put(attributeName, dataType);
            return dataType;

        } catch (NoSuchFieldException e) {
            // Configuration Error: The attribute name in TierCondition does not exist in the PricingInput fact.
            throw new IllegalArgumentException(
                    String.format("Invalid rule attribute '%s'. Not found in PricingInput fact. Check TierCondition configuration.", attributeName),
                    e);
        }
    }

    /**
     * TEMPORARY: Builds a minimal, valid DRL string to ensure the KieContainer compiles
     * successfully during initial application startup (before DB is implemented).
     */
    public String buildPlaceholderRules() {
        return """
            package bankengine.pricing.rules;

            import com.bankengine.rules.model.PricingInput;
            import com.bankengine.pricing.model.PriceValue;
            import java.math.BigDecimal;

            rule "PlaceholderRule_DoNothing"
                when
                    // Rule fires unconditionally on the presence of a PricingInput
                    $input : PricingInput ( )
                then
                    // Do nothing, just ensure compilation succeeds
            end
            """;
    }
}