package com.bankengine.pricing.service;

import com.bankengine.pricing.dto.ProductPricingCalculationResult.PriceComponentDetail;
import com.bankengine.pricing.model.PriceValue.ValueType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceAggregatorTest {

    private final PriceAggregator aggregator = new PriceAggregator();

    @Test
    void shouldCalculateComplexPricingMixCorrectly() {
        // ARRANGE: $1,000 transaction
        // + $10.00 (Fixed Fee)
        // + 2% Processing Fee ($20.00)
        // - $5.00 Senior Discount
        // - 1% Promo Discount ($10.00)
        // Expected: 10 + 20 - 5 - 10 = 15.00

        List<PriceComponentDetail> components = List.of(
                createDetail("BASE_FEE", "10.00", ValueType.ABSOLUTE),
                createDetail("PROC_FEE", "2.00", ValueType.PERCENTAGE),
                createDetail("SENIOR_DISC", "5.00", ValueType.DISCOUNT_ABSOLUTE),
                createDetail("PROMO_DISC", "1.00", ValueType.DISCOUNT_PERCENTAGE),
                createDetail("ATM_FREE", "5.00", ValueType.FREE_COUNT) // Should be ignored in math
        );

        // ACT
        BigDecimal result = aggregator.calculate(components, new BigDecimal("1000.00"));

        // ASSERT
        assertEquals(new BigDecimal("15.00"), result);
    }

    @Test
    void shouldReturnZeroIfDiscountsExceedFees() {
        List<PriceComponentDetail> components = List.of(
                createDetail("BASE_FEE", "10.00", ValueType.ABSOLUTE),
                createDetail("HUGE_DISC", "50.00", ValueType.DISCOUNT_ABSOLUTE)
        );

        BigDecimal result = aggregator.calculate(components, new BigDecimal("1000.00"));

        assertEquals(new BigDecimal("0.00"), result);
    }

    private PriceComponentDetail createDetail(String code, String amount, ValueType type) {
        return PriceComponentDetail.builder()
                .componentCode(code)
                .amount(new BigDecimal(amount))
                .valueType(type)
                .build();
    }
}