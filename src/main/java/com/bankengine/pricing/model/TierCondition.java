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
     * Converts this structured condition into a safe Drools expression fragment.
     * This method is intended to be called by DroolsRuleBuilderService, which must
     * provide the data type of the attribute to ensure correct DRL syntax (e.g., quoting strings).
     *
     * @param dataType The known data type of the attributeName field in the PricingInput fact
     * (e.g., "STRING", "INTEGER", "DECIMAL").
     * @return A DRL condition fragment (e.g., "segment == \"PREMIUM\"", "amount > 1000")
     */
    public String toDroolsExpression(String dataType) {
        if (attributeValue == null || attributeValue.trim().isEmpty()) {
            // This is a safety mechanism. Ideally, validation should prevent empty attribute values.
            return "true";
        }

        // Numeric (INTEGER, DECIMAL) and Boolean types do not need quotes in DRL.
        // STRING, DATE, etc., do require quotes. We assume the passed string maps to a well-known type.
        boolean needsQuotes = "STRING".equalsIgnoreCase(dataType) || "DATE".equalsIgnoreCase(dataType);

        switch (operator) {
            case EQ:
            case NE:
            case GT:
            case GE:
            case LT:
            case LE:
                String operatorSymbol = switch (operator) {
                    case EQ -> "==";
                    case NE -> "!=";
                    case GT -> ">";
                    case GE -> ">=";
                    case LT -> "<";
                    case LE -> "<=";
                    default ->
                        // This case is covered by the outer switch, but kept for robustness
                            throw new IllegalStateException("Unsupported relational operator: " + operator);
                };

                String formattedValue = needsQuotes
                        ? String.format("\"%s\"", attributeValue.trim())
                        : attributeValue.trim();

                // Example result: "segment == "PREMIUM"" or "amount > 1000"
                return String.format("%s %s %s", attributeName, operatorSymbol, formattedValue);

            case IN:
                // The 'attributeValue' is expected to be a comma-separated list of values.
                // Each item must be separated and quoted if required.
                String quotedValues = Arrays.stream(attributeValue.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(s -> needsQuotes ? String.format("\"%s\"", s) : s)
                        .collect(Collectors.joining(", "));

                // Example result: "segment in ( "PREMIUM", "STANDARD" )"
                return String.format("%s in ( %s )", attributeName, quotedValues);

            default:
                throw new IllegalStateException("Unsupported operator: " + operator);
        }
    }
}