package com.bankengine.pricing.service;

import com.bankengine.pricing.model.*;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.repository.PricingInputMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // To look up the data type for the flexible attributes
    @Autowired
    private PricingInputMetadataRepository metadataRepository;

    // Cache for attribute metadata to avoid repeated DB lookups during DRL generation.
    // Key: attributeName (from TierCondition), Value: PricingInputMetadata object
    private final Map<String, PricingInputMetadata> ATTRIBUTE_METADATA_CACHE = new ConcurrentHashMap<>();

    // --- DroolsRuleBuilderService Methods (Header, buildAllRules, etc. remain the same) ---

    private String getDrlHeader() {
        return """
            package bankengine.pricing.rules;
            
            import com.bankengine.rules.model.PricingInput;
            import com.bankengine.pricing.model.PriceValue;
            import java.math.BigDecimal;
            import java.util.Map;
            
            """;
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
        // Clear and pre-populate the cache with required metadata for this component's attributes
        // This is crucial to avoid N+1 issues and ensure metadata is available before rule building.
        Set<String> requiredAttributes = component.getPricingTiers().stream()
                .flatMap(tier -> tier.getConditions().stream())
                .map(TierCondition::getAttributeName)
                .collect(Collectors.toSet());

        loadMetadataCache(requiredAttributes);

        return component.getPricingTiers().stream()
                .map(tier -> buildSingleTierRule(component, tier))
                .collect(Collectors.joining("\n\n")); // Use double newline for separation
    }

    // NEW Helper: Loads required metadata from the DB into the cache
    private void loadMetadataCache(Set<String> attributeNames) {
        ATTRIBUTE_METADATA_CACHE.clear(); // Clear cache for component/full reload

        // Fetch all necessary metadata records in one query
        List<PricingInputMetadata> metadataList = metadataRepository.findByAttributeKeyIn(attributeNames);

        for (PricingInputMetadata metadata : metadataList) {
            ATTRIBUTE_METADATA_CACHE.put(metadata.getAttributeKey(), metadata);
        }
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


    // --- Fetches metadata and passes it to TierCondition ---
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

                // 2. Get the expression part using the metadata
                String expression = condition.toDroolsExpression(metadata);

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

    // --- NEW Helper Method: Resolves attribute metadata from cache/DB ---
    private PricingInputMetadata getFactAttributeMetadata(String attributeName) {
        // 1. Check cache first
        PricingInputMetadata metadata = ATTRIBUTE_METADATA_CACHE.get(attributeName);
        if (metadata != null) {
            return metadata;
        }

        // 2. Fallback to DB (Should ideally be prevented by loadMetadataCache, but included for robustness)
        metadata = metadataRepository.findByAttributeKey(attributeName)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Invalid rule attribute '%s'. Not found in PricingInputMetadata registry.", attributeName)));

        // Cache the result (though usually unnecessary if loadMetadataCache is called)
        ATTRIBUTE_METADATA_CACHE.put(attributeName, metadata);
        return metadata;
    }


    // --- DEPRECATED/REMOVED: The reflection method is no longer needed ---
    // private String getFactAttributeDataType(String attributeName) { ... }
    // This method is REMOVED as its logic is now contained in PricingInputMetadata.getFqnType()

    // --- buildRHSAction and buildPlaceholderRules remain identical ---
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
     * TEMPORARY: Builds a minimal, valid DRL string to ensure the KieContainer compiles.
     */
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