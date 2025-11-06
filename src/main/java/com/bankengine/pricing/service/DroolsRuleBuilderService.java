package com.bankengine.pricing.service;

import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.TierCondition;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.rules.model.PricingInput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service responsible for converting PricingComponent configuration (Tiers, Conditions, Values)
 * into a Drools Rule Language (DRL) string for runtime evaluation.
 */
@Service
public class DroolsRuleBuilderService {

    @Autowired
    private PricingComponentRepository componentRepository;

    // Cache for attribute types to avoid repeated reflection lookups.
    private static final Map<String, String> ATTRIBUTE_TYPE_CACHE = new ConcurrentHashMap<>();

    // --- For Single Component (Used for Testing/Single Reload) ---
    public String buildRules(PricingComponent component) {
        StringBuilder drl = new StringBuilder();
        drl.append(getDrlHeader());
        drl.append(generateComponentRulesBody(component));
        return drl.toString();
    }

    private String getDrlHeader() {
        return """
            package bankengine.pricing.rules;
            
            import com.bankengine.rules.model.PricingInput;
            import com.bankengine.pricing.model.PriceValue;
            import java.math.BigDecimal;
            
            """; // Trailing newline is included for proper spacing
    }

    // --- For Aggregation (Used for Startup) ---
    /**
     * Mark this method as transactional to keep the session open
     * while the rule builder iterates over the eagerly-fetched collections.
     */
    @Transactional(readOnly = true)
    public String buildAllRulesForCompilation() {
        StringBuilder finalDrl = new StringBuilder();

        // 1. Add package and common imports ONLY ONCE at the top
        finalDrl.append(getDrlHeader());

        // Use the eager-fetching query to initialize all collections
        List<PricingComponent> components = componentRepository.findAllEagerlyForRules();

        for (PricingComponent component : components) {
            // 2. Append ONLY the rule bodies for each component
            finalDrl.append(generateComponentRulesBody(component));
            finalDrl.append("\n\n// --- End Component: ").append(component.getName()).append(" ---\n\n");
        }

        if (components.isEmpty()) {
             // Safety check: if no components exist, return a valid empty DRL
             return buildPlaceholderRules();
        }

        return finalDrl.toString();
    }

    // --- Private Helper Method: Generates ONLY the rules, no header/package ---
    private String generateComponentRulesBody(PricingComponent component) {
        return component.getPricingTiers().stream()
                .map(tier -> buildSingleTierRule(component, tier))
                .collect(Collectors.joining("\n\n")); // Use double newline for separation
    }

    /**
     * Builds a single Drools rule for a specific Pricing Tier. (Existing Logic)
     */
    private String buildSingleTierRule(PricingComponent component, PricingTier tier) {
        StringBuilder rule = new StringBuilder();

        // 1. Rule Metadata
        String ruleName = String.format("Rule_%s_Tier_%d",
                component.getName().replaceAll("\\s", "_"),
                tier.getId());

        rule.append("rule \"").append(ruleName).append("\"\n");

        // 2. Left-Hand Side (WHEN)
        rule.append("    when\n");
        rule.append(buildLHSCondition(tier));

        // 3. Right-Hand Side (THEN)
        rule.append("    then\n");
        rule.append(buildRHSAction(tier));

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

                // 1. Get the expression part
                String dataType = getFactAttributeDataType(condition.getAttributeName());
                String expression = condition.toDroolsExpression(dataType);

                // Append the current condition expression
                conditionBuilder.append(expression);

                // 2. Check if there are MORE elements to follow
                if (iterator.hasNext()) {
                    // Append the connector from the CURRENT condition, defaulting to AND
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

    private String buildRHSAction(PricingTier tier) {
        StringBuilder rhs = new StringBuilder();

        if (tier.getPriceValues() != null && !tier.getPriceValues().isEmpty()) {
            // Get the first price value
            PriceValue priceValue = tier.getPriceValues().iterator().next();
            String priceAmount = priceValue.getPriceAmount().toPlainString();
            String valueType = priceValue.getValueType().name();
            String currency = priceValue.getCurrency();

            // Action: Update the PricingInput fact with the result
            rhs.append(String.format("        $input.setMatchedTierId(%dL);\n", tier.getId()));
            rhs.append(String.format("        $input.setPriceAmount(new BigDecimal(\"%s\"));\n", priceAmount));
            rhs.append(String.format("        $input.setValueType(\"%s\");\n", valueType));
            rhs.append(String.format("        $input.setCurrency(\"%s\");\n", currency));
            rhs.append("        $input.setRuleFired(true);\n");
            rhs.append("        update($input);\n");
        } else {
            // Optional: Handle case where a tier has no price value (e.g., a simple waiver or logging rule)
            rhs.append("// No price actions defined for this tier.\n");
        }

        return rhs.toString();
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
     * TEMPORARY: Builds a minimal, valid DRL string to ensure the KieContainer compiles.
     */
    public String buildPlaceholderRules() {
        return """
            package bankengine.pricing.rules;

            import com.bankengine.rules.model.PricingInput;
            import com.bankengine.pricing.model.PriceValue;
            import java.math.BigDecimal;

            rule "PlaceholderRule_DoNothing"
                when
                    $input : PricingInput ( )
                then
                    // Do nothing, just ensure compilation succeeds
            end
            """;
    }
}