package com.bankengine.pricing.service;

import com.bankengine.pricing.dto.ProductPricingCalculationResult.PriceComponentDetail;
import com.bankengine.pricing.model.PriceValue.ValueType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceAggregatorTest {

    private final PriceAggregator aggregator = new PriceAggregator();

    @Test
    @DisplayName("Waiver Check - Should result in no additional cost when type is WAIVED")
    void shouldHandleWaivedValueType() {
        // ARRANGE: $1,000 transaction
        // + $10.00 Base Fee
        // + $50.00 Service Fee (WAIVED)
        // Result should be exactly 10.00
        List<PriceComponentDetail> components = List.of(
                createComponentDetail("BASE_FEE", "10.00", ValueType.FEE_ABSOLUTE),
                createComponentDetail("VIP_FEE", "50.00", ValueType.WAIVED)
        );

        // ACT
        BigDecimal result = aggregator.calculate(components, new BigDecimal("10.00"));

        // ASSERT
        assertEquals(new BigDecimal("10.00").setScale(2, RoundingMode.HALF_UP), result);
    }

    @Test
    @DisplayName("Free Count - Should ignore non-monetary components like FREE_COUNT")
    void shouldIgnoreFreeCountInCalculation() {
        // ARRANGE: $1,000 transaction
        // + $5.00 Fee
        // + 5 (FREE_COUNT) -> This represents an entitlement, not a price
        List<PriceComponentDetail> components = List.of(
                createComponentDetail("FIXED_FEE", "5.00", ValueType.FEE_ABSOLUTE),
                createComponentDetail("ATM_ENTITLEMENT", "5.00", ValueType.FREE_COUNT)
        );

        // ACT
        BigDecimal result = aggregator.calculate(components, new BigDecimal("10.00"));

        // ASSERT
        assertEquals(new BigDecimal("5.00").setScale(2, RoundingMode.HALF_UP), result);
    }

    @Test
    @DisplayName("Aggregate Mix - Should correctly sum absolute and percentage fees and subtract discounts")
    void shouldCalculateComplexPricingMixCorrectly() {
        // ARRANGE: $1,000 transaction
        // + $10.00 (Fixed Fee)
        // + 2% Processing Fee ($20.00) -> Total Fees = $30.00
        // - $5.00 Senior Discount
        // - 1% Promo Discount (1% of $30.00 = $0.30)
        // Total Expected: 30.00 - 5.00 - 0.30 = 24.70

        List<PriceComponentDetail> components = List.of(
                createComponentDetail("BASE_FEE", "10.00", ValueType.FEE_ABSOLUTE),
                createComponentDetail("PROC_FEE", "2.00", ValueType.FEE_PERCENTAGE),
                createComponentDetail("SENIOR_DISC", "5.00", ValueType.DISCOUNT_ABSOLUTE),
                createComponentDetail("PROMO_DISC", "1.00", ValueType.DISCOUNT_PERCENTAGE),
                createComponentDetail("ATM_FREE", "5.00", ValueType.FREE_COUNT) // Should be ignored in math
        );

        BigDecimal result = aggregator.calculate(components, new BigDecimal("1000.00"));
        assertEquals(new BigDecimal("24.70").setScale(2, RoundingMode.HALF_UP),
                result.setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    @DisplayName("Floor Zero - Total pricing should never be negative (fees cannot be less than zero)")
    void shouldReturnZeroIfDiscountsExceedFees() {
        List<PriceComponentDetail> components = List.of(
                createComponentDetail("BASE_FEE", "10.00", ValueType.FEE_ABSOLUTE),
                createComponentDetail("HUGE_DISC", "50.00", ValueType.DISCOUNT_ABSOLUTE)
        );

        BigDecimal result = aggregator.calculate(components, new BigDecimal("10.00"));

        assertEquals(new BigDecimal("0.00").setScale(2, RoundingMode.HALF_UP),
                result.setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    @DisplayName("Rounding - Should handle fractional percentages correctly (e.g., 1.75%)")
    void shouldHandleFractionalPercentages() {
        // ARRANGE: $100.00 transaction * 1.75% = $1.75
        List<PriceComponentDetail> components = List.of(
                createComponentDetail("FRACTIONAL_FEE", "1.75", ValueType.FEE_PERCENTAGE)
        );

        // ACT: Pass 100.00 as the transaction amount
        BigDecimal result = aggregator.calculate(components, new BigDecimal("100.00"));

        // ASSERT
        assertEquals(new BigDecimal("1.75").setScale(2, RoundingMode.HALF_UP), result);
    }

    private PriceComponentDetail createComponentDetail(String code, String amount, ValueType type) {
        return PriceComponentDetail.builder()
                .componentCode(code)
                .rawValue(new BigDecimal(amount))
                .valueType(type)
                .build();
    }
}