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
        TierCondition condition = new TierCondition();
        condition.setAttributeName("amount");
        condition.setOperator(Operator.EQ);
        condition.setAttributeValue("500.00");

        PricingInputMetadata metadata = new PricingInputMetadata();
        metadata.setDataType("DECIMAL"); // getFqnType() returns java.math.BigDecimal

        String result = builder.buildExpression(condition, metadata);

        assertEquals("((java.math.BigDecimal) customAttributes[\"amount\"]).compareTo(new java.math.BigDecimal(\"500.00\")) == 0", result);
    }

    @Test
    @DisplayName("Integer GT - Should use standard relational operator with Long cast")
    void testBuildExpression_Integer_GT() {
        TierCondition condition = new TierCondition();
        condition.setAttributeName("age");
        condition.setOperator(Operator.GT);
        condition.setAttributeValue("18");

        PricingInputMetadata metadata = new PricingInputMetadata();
        metadata.setDataType("INTEGER"); // getFqnType() returns java.lang.Long

        String result = builder.buildExpression(condition, metadata);

        assertEquals("((java.lang.Long) customAttributes[\"age\"]) > 18", result);
    }

    @Test
    @DisplayName("String IN - Should format list with parentheses and quotes")
    void testBuildExpression_String_IN() {
        TierCondition condition = new TierCondition();
        condition.setAttributeName("segment");
        condition.setOperator(Operator.IN);
        condition.setAttributeValue("RETAIL, PREMIUM");

        PricingInputMetadata metadata = new PricingInputMetadata();
        metadata.setDataType("STRING");

        String result = builder.buildExpression(condition, metadata);

        // Matching specific format: accessPath in ( "RETAIL", "PREMIUM" )
        assertEquals("customAttributes[\"segment\"] in ( \"RETAIL\", \"PREMIUM\" )", result);
    }

    @Test
    @DisplayName("Boolean EQ - Should not use quotes for boolean values")
    void testBuildExpression_Boolean_EQ() {
        TierCondition condition = new TierCondition();
        condition.setAttributeName("isStaff");
        condition.setOperator(Operator.EQ);
        condition.setAttributeValue("true");

        PricingInputMetadata metadata = new PricingInputMetadata();
        metadata.setDataType("BOOLEAN"); // getFqnType() returns java.lang.Boolean

        String result = builder.buildExpression(condition, metadata);

        assertEquals("((java.lang.Boolean) customAttributes[\"isStaff\"]) == true", result);
    }

    @Test
    @DisplayName("Empty Value - Should return 'true' as a safe DRL fragment")
    void testBuildExpression_EmptyValue() {
        TierCondition condition = new TierCondition();
        condition.setAttributeValue("");

        String result = builder.buildExpression(condition, null);

        assertEquals("true", result);
    }

    @Test
    @DisplayName("BigDecimal LT - Should verify Less Than branch")
    void testBuildExpression_BigDecimal_LT() {
        TierCondition condition = new TierCondition();
        condition.setAttributeName("limit");
        condition.setOperator(Operator.LT);
        condition.setAttributeValue("100");

        PricingInputMetadata metadata = new PricingInputMetadata();
        metadata.setDataType("DECIMAL");

        String result = builder.buildExpression(condition, metadata);

        assertTrue(result.contains(".compareTo("));
        assertTrue(result.endsWith("< 0"));
    }
}