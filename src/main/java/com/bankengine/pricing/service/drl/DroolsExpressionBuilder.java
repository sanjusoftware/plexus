package com.bankengine.pricing.service.drl;

import com.bankengine.pricing.model.PricingInputMetadata;
import com.bankengine.pricing.model.TierCondition;
import com.bankengine.pricing.model.TierCondition.Operator;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Utility responsible for generating the Drools Rule Language (DRL) expression fragment
 * for a single TierCondition.
 * This class is stateless and handles all type casting and syntax formatting required by Drools.
 */
@Component
public class DroolsExpressionBuilder {

    /**
     * Converts a structured TierCondition into a safe Drools expression fragment
     * using the customAttributes map access syntax and performing necessary type casting.
     *
     * @param condition The TierCondition object defining the rule.
     * @param metadata The PricingInputMetadata for this condition's attributeName.
     * @return A DRL condition fragment (e.g., "((java.math.BigDecimal) customAttributes["amount"]) > 1000")
     */
    public String buildExpression(TierCondition condition, PricingInputMetadata metadata) {
        String attributeValue = condition.getAttributeValue();
        Operator operator = condition.getOperator();
        String attributeName = condition.getAttributeName();

        if (attributeValue == null || attributeValue.trim().isEmpty()) {
            return "true";
        }

        String dataType = metadata.getDataType().toUpperCase();
        boolean needsQuotes = "STRING".equals(dataType) || "DATE".equals(dataType);

        // 1. Determine the fact property access path and casting
        String accessPath;
        // DRL Map Access: customAttributes["attributeName"]
        String mapAccess = String.format("customAttributes[\"%s\"]", attributeName);

        // If the type is not String (default Map return type), we must cast it.
        if (!"STRING".equals(dataType)) {
            // Example: ((java.math.BigDecimal) customAttributes["transactionAmount"])
            accessPath = String.format("((%s) %s)", metadata.getFqnType(), mapAccess);
        } else {
            accessPath = mapAccess;
        }

        // 2. Build the operator and formatted value
        if (operator == Operator.IN) {
            String quotedValues = Arrays.stream(attributeValue.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> needsQuotes ? String.format("\"%s\"", s) : s)
                    .collect(Collectors.joining(", "));

            // Example: customAttributes["segment"] in ( "PREMIUM", "STANDARD" )
            return String.format("%s in ( %s )", accessPath, quotedValues);
        }

        // Handle relational operators (EQ, GT, LT, etc.)
        String operatorSymbol = switch (operator) {
            case EQ -> "=="; case NE -> "!="; case GT -> ">";
            case GE -> ">="; case LT -> "<"; case LE -> "<=";
            default -> throw new IllegalStateException("Unsupported operator: " + operator);
        };

        String formattedValue = needsQuotes
                ? String.format("\"%s\"", attributeValue.trim())
                : attributeValue.trim();

        // 3. Combine access path, operator, and value
        // Example result: "((java.math.BigDecimal) customAttributes["amount"]) > 1000"
        return String.format("%s %s %s", accessPath, operatorSymbol, formattedValue);
    }
}