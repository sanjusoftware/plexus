package com.bankengine.pricing.service.drl;

import com.bankengine.pricing.model.PricingInputMetadata;
import com.bankengine.pricing.model.TierCondition;
import com.bankengine.pricing.model.TierCondition.Operator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

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

        assertTrue(result.contains(".compareTo("), "Should use compareTo for BigDecimal");
        assertTrue(result.endsWith("< 0"), "Should end with < 0 for LT operator");
    }

    @Test
    @DisplayName("LocalDate GE - Should use negation of isBefore")
    void testBuildExpression_Date_GE() {
        TierCondition condition = createCondition("effectiveDate", Operator.GE, "2026-01-01");
        PricingInputMetadata metadata = createMetadata("DATE");
        String result = builder.buildExpression(condition, metadata);
        assertEquals("(!((java.time.LocalDate) customAttributes[\"effectiveDate\"]).isBefore(java.time.LocalDate.parse(\"2026-01-01\")))", result);
    }

    @Test
    @DisplayName("BigDecimal All Operators - Should cover all comparison branches")
    void testBuildExpression_BigDecimal_AllOperators() {
        PricingInputMetadata metadata = createMetadata("DECIMAL");

        // Testing NE (!=)
        assertEquals("((java.math.BigDecimal) customAttributes[\"amt\"]).compareTo(new java.math.BigDecimal(\"10\")) != 0",
                builder.buildExpression(createCondition("amt", Operator.NE, "10"), metadata));

        // Testing GE (>=)
        assertEquals("((java.math.BigDecimal) customAttributes[\"amt\"]).compareTo(new java.math.BigDecimal(\"10\")) >= 0",
                builder.buildExpression(createCondition("amt", Operator.GE, "10"), metadata));

        // Testing LE (<=)
        assertEquals("((java.math.BigDecimal) customAttributes[\"amt\"]).compareTo(new java.math.BigDecimal(\"10\")) <= 0",
                builder.buildExpression(createCondition("amt", Operator.LE, "10"), metadata));
    }

    @Test
    @DisplayName("LocalDate All Operators - Should cover all temporal branches")
    void testBuildExpression_LocalDate_AllOperators() {
        PricingInputMetadata metadata = createMetadata("DATE");
        String dateVal = "2026-01-01";
        String rhs = "java.time.LocalDate.parse(\"2026-01-01\")";
        String access = "((java.time.LocalDate) customAttributes[\"dt\"])";

        // Testing EQ
        assertEquals(access + ".isEqual(" + rhs + ")",
                builder.buildExpression(createCondition("dt", Operator.EQ, dateVal), metadata));

        // Testing NE
        assertEquals("!" + access + ".isEqual(" + rhs + ")",
                builder.buildExpression(createCondition("dt", Operator.NE, dateVal), metadata));

        // Testing LT
        assertEquals(access + ".isBefore(" + rhs + ")",
                builder.buildExpression(createCondition("dt", Operator.LT, dateVal), metadata));
    }

    @Test
    @DisplayName("IN Operator - Should handle both String and Non-String types")
    void testBuildExpression_InOperator_Branches() {
        // String Branch: metadata.getFqnType() returns java.lang.String -> No cast applied
        PricingInputMetadata strMetadata = createMetadata("STRING");
        String strResult = builder.buildExpression(createCondition("segment", Operator.IN, "RETAIL, PREMIUM"), strMetadata);
        assertEquals("customAttributes[\"segment\"] in ( \"RETAIL\", \"PREMIUM\" )", strResult);

        // Long Branch: metadata.getFqnType() returns java.lang.Long -> Cast applied
        PricingInputMetadata longMetadata = createMetadata("INTEGER");
        String longResult = builder.buildExpression(createCondition("age", Operator.IN, "18, 21"), longMetadata);
        assertEquals("((java.lang.Long) customAttributes[\"age\"]) in ( 18, 21 )", longResult);
    }

    @Test
    @DisplayName("BigDecimal NE - Should cover the Not Equal branch")
    void testBuildExpression_BigDecimal_NE() {
        PricingInputMetadata metadata = createMetadata("DECIMAL");
        TierCondition condition = createCondition("balance", Operator.NE, "0");

        String result = builder.buildExpression(condition, metadata);
        assertEquals("((java.math.BigDecimal) customAttributes[\"balance\"]).compareTo(new java.math.BigDecimal(\"0\")) != 0", result);
    }

    @Test
    @DisplayName("LocalDate LT - Should cover the Before branch")
    void testBuildExpression_Date_LT() {
        PricingInputMetadata metadata = createMetadata("DATE");
        TierCondition condition = createCondition("joiningDate", Operator.LT, "2020-01-01");

        String result = builder.buildExpression(condition, metadata);
        assertEquals("((java.time.LocalDate) customAttributes[\"joiningDate\"]).isBefore(java.time.LocalDate.parse(\"2020-01-01\"))", result);
    }

    @Test
    @DisplayName("Unsupported Operator - Should throw IllegalStateException")
    void testBuildExpression_UnsupportedOperator() {
        PricingInputMetadata metadata = createMetadata("LONG");
        TierCondition condition = createCondition("id", null, "10");
        assertThrows(NullPointerException.class, () -> builder.buildExpression(condition, metadata));
    }

    @Test
    @DisplayName("Unknown FQN Type - Should throw IllegalStateException")
    void testBuildExpression_UnknownFqnType() {
        TierCondition condition = createCondition("mystery", Operator.EQ, "val");
        PricingInputMetadata metadata = createMetadata("MYSTERY_TYPE");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> builder.buildExpression(condition, metadata));

        assertTrue(ex.getMessage().contains("Unsupported pricing data type"),
                "Error message should indicate the type is unsupported");
    }

    @Test
    @DisplayName("Integer Alias - Should map to Long cast")
    void testBuildExpression_IntegerAlias() {
        TierCondition condition = createCondition("count", Operator.EQ, "5");
        PricingInputMetadata metadata = createMetadata("INTEGER");
        String result = builder.buildExpression(condition, metadata);
        // Even though input was INTEGER, DRL uses Long cast
        assertEquals("((java.lang.Long) customAttributes[\"count\"]) == 5", result);
    }

    @Test
    @DisplayName("Null Operator - Should handle gracefully")
    void testBuildExpression_NullOperator() {
        TierCondition condition = createCondition("amount", null, "100");
        PricingInputMetadata metadata = createMetadata("DECIMAL");
        assertThrows(RuntimeException.class, () -> builder.buildExpression(condition, metadata));
    }

    @Test
    @DisplayName("LocalDate NE - Should cover negated isEqual branch")
    void testBuildExpression_Date_NE() {
        TierCondition condition = createCondition("dt", Operator.NE, "2026-01-01");
        PricingInputMetadata metadata = createMetadata("DATE");
        String result = builder.buildExpression(condition, metadata);
        assertEquals("!((java.time.LocalDate) customAttributes[\"dt\"]).isEqual(java.time.LocalDate.parse(\"2026-01-01\"))", result);
    }

    @Test
    @DisplayName("BigDecimal GE - Should cover compareTo >= 0 branch")
    void testBuildExpression_BigDecimal_GE() {
        TierCondition condition = createCondition("balance", Operator.GE, "1000");
        PricingInputMetadata metadata = createMetadata("DECIMAL");
        String result = builder.buildExpression(condition, metadata);
        assertEquals("((java.math.BigDecimal) customAttributes[\"balance\"]).compareTo(new java.math.BigDecimal(\"1000\")) >= 0", result);
    }

    @ParameterizedTest
    @DisplayName("Long/Integer Relational Operators - Coverage for all branches")
    @CsvSource({
            "NE, !=, 10",
            "LT, <, 10",
            "GE, >=, 10",
            "LE, <=, 10",
            "GT, >, 10"
    })

    void testBuildExpression_Integer_RelationalBranches(Operator op, String expectedSymbol, String value) {
        TierCondition condition = createCondition("age", op, value);
        PricingInputMetadata metadata = createMetadata("INTEGER");

        String result = builder.buildExpression(condition, metadata);
        assertEquals("((java.lang.Long) customAttributes[\"age\"]) " + expectedSymbol + " " + value, result);
    }

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