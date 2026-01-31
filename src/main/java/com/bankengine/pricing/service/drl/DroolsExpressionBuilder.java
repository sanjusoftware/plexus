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
        String fqnType = metadata.getFqnType();
        boolean needsQuotes = "STRING".equals(dataType) || "DATE".equals(dataType);

        // 1. Determine the access path and casting
        String mapAccess = String.format("customAttributes[\"%s\"]", attributeName);
        String accessPath = (!"STRING".equals(dataType))
                ? String.format("((%s) %s)", fqnType, mapAccess)
                : mapAccess;

        // 2. Handle the 'IN' operator (Shared across types)
        if (operator == Operator.IN) {
            String quotedValues = Arrays.stream(attributeValue.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> needsQuotes ? String.format("\"%s\"", s) : s)
                    .collect(Collectors.joining(", "));
            return String.format("%s in ( %s )", accessPath, quotedValues);
        }

        // 3. Type-Specific Relational Logic
        if ("java.math.BigDecimal".equals(fqnType)) {
            String rhs = String.format("new java.math.BigDecimal(\"%s\")", attributeValue.trim());
            return switch (operator) {
                case EQ -> String.format("%s.compareTo(%s) == 0", accessPath, rhs);
                case NE -> String.format("%s.compareTo(%s) != 0", accessPath, rhs);
                case GT -> String.format("%s.compareTo(%s) > 0", accessPath, rhs);
                case GE -> String.format("%s.compareTo(%s) >= 0", accessPath, rhs);
                case LT -> String.format("%s.compareTo(%s) < 0", accessPath, rhs);
                case LE -> String.format("%s.compareTo(%s) <= 0", accessPath, rhs);
                default -> throw new IllegalStateException("Unsupported BigDecimal operator: " + operator);
            };
        }

        if ("java.time.LocalDate".equals(fqnType)) {
            // RHS: java.time.LocalDate.parse("2026-01-31")
            String rhs = String.format("java.time.LocalDate.parse(\"%s\")", attributeValue.trim());
            return switch (operator) {
                case EQ -> String.format("%s.isEqual(%s)", accessPath, rhs);
                case NE -> String.format("!%s.isEqual(%s)", accessPath, rhs);
                case GT -> String.format("%s.isAfter(%s)", accessPath, rhs);
                case GE -> String.format("(!%s.isBefore(%s))", accessPath, rhs);
                case LT -> String.format("%s.isBefore(%s)", accessPath, rhs);
                case LE -> String.format("(!%s.isAfter(%s))", accessPath, rhs);
                default -> throw new IllegalStateException("Unsupported Date operator: " + operator);
            };
        }

        // 4. Default Relational Logic (Long, Boolean, String EQ)
        String operatorSymbol = switch (operator) {
            case EQ -> "==";
            case NE -> "!=";
            case GT -> ">";
            case GE -> ">=";
            case LT -> "<";
            case LE -> "<=";
            default -> throw new IllegalStateException("Unsupported operator: " + operator);
        };

        String formattedValue = needsQuotes ? String.format("\"%s\"", attributeValue.trim()) : attributeValue.trim();
        return String.format("%s %s %s", accessPath, operatorSymbol, formattedValue);
    }
}