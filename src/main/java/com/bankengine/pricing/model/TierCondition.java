package com.bankengine.pricing.model;

import com.bankengine.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.stream.Collectors;

@Entity
@Table(name = "tier_condition")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TierCondition extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The Pricing Tier this condition belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pricing_tier_id", nullable = false)
    private PricingTier pricingTier;

    // 1. The Attribute (Field) to check in the PricingInput object
    // e.g., "segment", "amount", "clientType", "channel"
    @Column(nullable = false)
    private String attributeName;

    // 2. The Operator (Dropdown in UI)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Operator operator;

    // 3. The Value (Input field in UI)
    // e.g., "PREMIUM", "500000", "true"
    @Column(nullable = false)
    private String attributeValue;

    // 4. The Logical Connector (For combining multiple conditions)
    // e.g., AND, OR (only applicable if there is a next condition)
    @Enumerated(EnumType.STRING)
    private LogicalConnector connector;

    public enum Operator {
        EQ, // == (Equal)
        NE, // != (Not Equal)
        GT, // > (Greater Than)
        GE, // >= (Greater or Equal)
        LT, // < (Less Than)
        LE, // <= (Less or Equal)
        IN // In a list (e.g., segment IN ('A', 'B'))
    }

    public enum LogicalConnector {
        AND, OR
    }

    /**
     * Converts this structured condition into a safe Drools expression fragment
     * using the customAttributes map access syntax and performing necessary type casting.
     *
     * @param metadata The PricingInputMetadata for this TierCondition's attributeName.
     * @return A DRL condition fragment (e.g., "((java.math.BigDecimal) customAttributes["amount"]) > 1000")
     */
    public String toDroolsExpression(PricingInputMetadata metadata) {
        if (attributeValue == null || attributeValue.trim().isEmpty()) {
            return "true";
        }

        String dataType = metadata.getDataType().toUpperCase();

        // DRL requires quotes for Strings but not for numeric/Boolean types
        boolean needsQuotes = "STRING".equals(dataType) || "DATE".equals(dataType);

        // 1. Determine the fact property access path
        String accessPath;
        // DRL Map Access: customAttributes["attributeName"]
        String mapAccess = String.format("customAttributes[\"%s\"]", attributeName);

        // If the type is not String (which is the default return type of Map.get(Object)),
        // we must cast it for Drools to perform numeric/boolean comparison.
        if (!"STRING".equals(dataType)) {
            // Example: ((java.math.BigDecimal) customAttributes["transactionAmount"])
            accessPath = String.format("((%s) %s)", metadata.getFqnType(), mapAccess);
        } else {
            accessPath = mapAccess;
        }

        // 2. Build the operator and formatted value
        String operatorSymbol;
        String formattedValue;

        if (operator == Operator.IN) {
            // Logic for IN operator remains similar, but applies to the map access path
            String quotedValues = Arrays.stream(attributeValue.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> needsQuotes ? String.format("\"%s\"", s) : s)
                    .collect(Collectors.joining(", "));

            // Example: customAttributes["segment"] in ( "PREMIUM", "STANDARD" )
            return String.format("%s in ( %s )", accessPath, quotedValues);
        }

        // Handle relational operators (EQ, GT, LT, etc.)
        operatorSymbol = switch (operator) {
            case EQ -> "=="; case NE -> "!="; case GT -> ">";
            case GE -> ">="; case LT -> "<"; case LE -> "<=";
            default -> throw new IllegalStateException("Unsupported operator: " + operator);
        };

        formattedValue = needsQuotes
            ? String.format("\"%s\"", attributeValue.trim())
            : attributeValue.trim();

        // 3. Combine access path, operator, and value
        // Example result: "((java.math.BigDecimal) customAttributes["amount"]) > 1000"
        return String.format("%s %s %s", accessPath, operatorSymbol, formattedValue);
    }
}