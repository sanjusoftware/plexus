package com.bankengine.pricing.service.drl;

import com.bankengine.pricing.model.PricingDataType;
import com.bankengine.pricing.model.PricingInputMetadata;
import com.bankengine.pricing.model.TierCondition;
import com.bankengine.pricing.model.TierCondition.Operator;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class DroolsExpressionBuilder {

    public String buildExpression(TierCondition condition, PricingInputMetadata metadata) {
        String val = condition.getAttributeValue();
        Operator op = condition.getOperator();

        if (val == null || val.trim().isEmpty()) return "true";

        PricingDataType type = PricingDataType.fromString(metadata.getDataType());
        String fqn = type.getFqn();
        boolean quoted = type.isQuoted();

        String access = String.format("customAttributes[\"%s\"]", condition.getAttributeName());
        String path = (type != PricingDataType.STRING) ? String.format("((%s) %s)", fqn, access) : access;

        if (op == Operator.IN) {
            String list = Arrays.stream(val.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(s -> quoted ? "\"" + s + "\"" : s)
                    .collect(Collectors.joining(", "));
            return String.format("%s in ( %s )", path, list);
        }

        if (type == PricingDataType.DECIMAL) {
            String rhs = "new java.math.BigDecimal(\"" + val.trim() + "\")";
            return switch (op) {
                case EQ -> path + ".compareTo(" + rhs + ") == 0";
                case NE -> path + ".compareTo(" + rhs + ") != 0";
                case GT -> path + ".compareTo(" + rhs + ") > 0";
                case GE -> path + ".compareTo(" + rhs + ") >= 0";
                case LT -> path + ".compareTo(" + rhs + ") < 0";
                case LE -> path + ".compareTo(" + rhs + ") <= 0";
                default -> throw new IllegalStateException("Bad Decimal Op: " + op);
            };
        }

        if (type == PricingDataType.DATE) {
            String rhs = "java.time.LocalDate.parse(\"" + val.trim() + "\")";
            return switch (op) {
                case EQ -> path + ".isEqual(" + rhs + ")";
                case NE -> "!" + path + ".isEqual(" + rhs + ")";
                case GT -> path + ".isAfter(" + rhs + ")";
                case GE -> "(!" + path + ".isBefore(" + rhs + "))";
                case LT -> path + ".isBefore(" + rhs + ")";
                case LE -> "(!" + path + ".isAfter(" + rhs + "))";
                default -> throw new IllegalStateException("Bad Date Op: " + op);
            };
        }

        String symbol = switch (op) {
            case EQ -> "==";
            case NE -> "!=";
            case GT -> ">";
            case GE -> ">=";
            case LT -> "<";
            case LE -> "<=";
            default -> throw new IllegalStateException("Bad Op: " + op);
        };

        String finalVal = quoted ? "\"" + val.trim() + "\"" : val.trim();
        return String.format("%s %s %s", path, symbol, finalVal);
    }
}