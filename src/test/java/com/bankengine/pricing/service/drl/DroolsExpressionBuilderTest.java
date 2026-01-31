package com.bankengine.pricing.service.drl;

import com.bankengine.pricing.model.PricingInputMetadata;
import com.bankengine.pricing.model.TierCondition;
import com.bankengine.pricing.model.TierCondition.Operator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroolsExpressionBuilderTest {

    private DroolsExpressionBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new DroolsExpressionBuilder();
    }

    @Test
    @DisplayName("BigDecimal EQ - Should use compareTo syntax")
    void testBuildExpression_BigDecimal_EQ() {
        TierCondition condition = createCondition("amount", Operator.EQ, "500.00");
        PricingInputMetadata metadata = createMetadata("DECIMAL");

        String result = builder.buildExpression(condition, metadata);
        assertEquals("((java.math.BigDecimal) customAttributes[\"amount\"]).compareTo(new java.math.BigDecimal(\"500.00\")) == 0", result);
    }

    @Test
    @DisplayName("LocalDate GT - Should use isAfter syntax")
    void testBuildExpression_Date_GT() {
        TierCondition condition = createCondition("effectiveDate", Operator.GT, "2026-01-01");
        PricingInputMetadata metadata = createMetadata("DATE");

        String result = builder.buildExpression(condition, metadata);
        assertEquals("((java.time.LocalDate) customAttributes[\"effectiveDate\"]).isAfter(java.time.LocalDate.parse(\"2026-01-01\"))", result);
    }

    @Test
    @DisplayName("LocalDate LE - Should use !isAfter syntax")
    void testBuildExpression_Date_LE() {
        TierCondition condition = createCondition("expiryDate", Operator.LE, "2030-12-31");
        PricingInputMetadata metadata = createMetadata("DATE");

        String result = builder.buildExpression(condition, metadata);
        assertEquals("(!((java.time.LocalDate) customAttributes[\"expiryDate\"]).isAfter(java.time.LocalDate.parse(\"2030-12-31\")))", result);
    }

    @Test
    @DisplayName("Integer GT - Should use standard relational operator with Long cast")
    void testBuildExpression_Integer_GT() {
        TierCondition condition = createCondition("age", Operator.GT, "18");
        PricingInputMetadata metadata = createMetadata("INTEGER");

        String result = builder.buildExpression(condition, metadata);
        assertEquals("((java.lang.Long) customAttributes[\"age\"]) > 18", result);
    }

    @Test
    @DisplayName("String IN - Should format list with parentheses and quotes")
    void testBuildExpression_String_IN() {
        TierCondition condition = createCondition("segment", Operator.IN, "RETAIL, PREMIUM");
        PricingInputMetadata metadata = createMetadata("STRING");

        String result = builder.buildExpression(condition, metadata);
        assertEquals("customAttributes[\"segment\"] in ( \"RETAIL\", \"PREMIUM\" )", result);
    }

    @Test
    @DisplayName("Boolean EQ - Should not use quotes for boolean values")
    void testBuildExpression_Boolean_EQ() {
        TierCondition condition = createCondition("isStaff", Operator.EQ, "true");
        PricingInputMetadata metadata = createMetadata("BOOLEAN");

        String result = builder.buildExpression(condition, metadata);
        assertEquals("((java.lang.Boolean) customAttributes[\"isStaff\"]) == true", result);
    }

    @Test
    @DisplayName("Empty Value - Should return 'true'")
    void testBuildExpression_EmptyValue() {
        TierCondition condition = new TierCondition();
        condition.setAttributeValue("");
        assertEquals("true", builder.buildExpression(condition, null));
    }

    @Test
    @DisplayName("BigDecimal LT - Should verify Less Than branch with compareTo")
    void testBuildExpression_BigDecimal_LT() {
        TierCondition condition = createCondition("limit", Operator.LT, "100.00");
        PricingInputMetadata metadata = createMetadata("DECIMAL");

        String result = builder.buildExpression(condition, metadata);

        // Should result in: .compareTo(...) < 0
        assertTrue(result.contains(".compareTo("), "Should use compareTo for BigDecimal");
        assertTrue(result.endsWith("< 0"), "Should end with < 0 for LT operator");
    }

    @Test
    @DisplayName("LocalDate GE - Should use negation of isBefore")
    void testBuildExpression_Date_GE() {
        TierCondition condition = createCondition("effectiveDate", Operator.GE, "2026-01-01");
        PricingInputMetadata metadata = createMetadata("DATE");

        String result = builder.buildExpression(condition, metadata);

        // Logic: (Not Before) is equivalent to (Greater than or Equal)
        assertEquals("(!((java.time.LocalDate) customAttributes[\"effectiveDate\"]).isBefore(java.time.LocalDate.parse(\"2026-01-01\")))", result);
    }

    // Helper methods for cleaner tests
    private TierCondition createCondition(String name, Operator op, String val) {
        TierCondition c = new TierCondition();
        c.setAttributeName(name);
        c.setOperator(op);
        c.setAttributeValue(val);
        return c;
    }

    private PricingInputMetadata createMetadata(String type) {
        PricingInputMetadata m = new PricingInputMetadata();
        m.setDataType(type);
        return m;
    }
}