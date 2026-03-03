package com.bankengine.pricing.service;

import com.bankengine.pricing.dto.ProductPricingCalculationResult.PriceComponentDetail;
import com.bankengine.pricing.model.PriceValue.ValueType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceAggregatorTest {

    private final PriceAggregator aggregator = new PriceAggregator();
    private static final int SCALE = 2;

    @Test
    @DisplayName("Complex Mix - Should handle targeted and global discounts correctly")
    void shouldHandleTargetedDiscounts() {
        // ARRANGE: $1,000 Product Base Fee
        // Fees: $10 Base Fee + $20 (2% Proc Fee of $1000) = $30 Bundle Fees
        // Total Pool for Global: $1000 (Product) + $30 (Bundle Fees) = $1030

        // Discounts:
        // 1. 50% targeted at 'BASE_FEE' ($10.00) = $5.00
        // 2. 10% Global ($1030.00 * 0.10) = $103.00
        // Net Impact: $30.00 (Fees) - $5.00 (Targeted) - $103.00 (Global) = -$78.00

        List<PriceComponentDetail> components = List.of(
                createComponentDetail("BASE_FEE", "10.00", ValueType.FEE_ABSOLUTE, null),
                createComponentDetail("PROC_FEE", "2.00", ValueType.FEE_PERCENTAGE, null),
                createComponentDetail("TARGET_DISC", "50.00", ValueType.DISCOUNT_PERCENTAGE, "BASE_FEE"),
                createComponentDetail("GLOBAL_DISC", "10.00", ValueType.DISCOUNT_PERCENTAGE, null)
        );

        BigDecimal productBaseFee = new BigDecimal("1000.00");

        BigDecimal netImpact = aggregator.calculateBundleImpact(components, productBaseFee, productBaseFee, null, LocalDate.now());

        assertScaledBigDecimal("-78.00", netImpact);
    }

    @Test
    @DisplayName("Safety: Should return $0 discount impact if target component code does not exist")
    void shouldReturnZeroForMissingTargetComponent() {
        List<PriceComponentDetail> components = List.of(
                createComponentDetail("BASE_FEE", "10.00", ValueType.FEE_ABSOLUTE, null),
                createComponentDetail("ORPHAN_DISC", "50.00", ValueType.DISCOUNT_PERCENTAGE, "NON_EXISTENT")
        );

        // $10.00 fee + 0.00 (invalid target discount) = $10.00 impact
        BigDecimal netImpact = aggregator.calculateBundleImpact(components, BigDecimal.ZERO,  BigDecimal.ZERO, null, LocalDate.now());

        assertScaledBigDecimal("10.00", netImpact);
    }

    @Test
    @DisplayName("Pro-rata Check - Should calculate partial fee for mid-month enrollment")
    void shouldHandleProRataCalculation() {
        // ARRANGE: Monthly fee of $30.00
        List<PriceComponentDetail> components = List.of(
                PriceComponentDetail.builder()
                        .componentCode("MONTHLY_FEE")
                        .rawValue(new BigDecimal("30.00"))
                        .valueType(ValueType.FEE_ABSOLUTE)
                        .proRataApplicable(true)
                        .build()
        );

        // Enroll mid-month (16th of 30-day June) = 15 days active = 50%
        LocalDate enrollment = LocalDate.of(2024, 6, 16);
        LocalDate effective = LocalDate.of(2024, 6, 30);

        BigDecimal netImpact = aggregator.calculateBundleImpact(components, BigDecimal.ZERO, BigDecimal.ZERO, enrollment, effective);

        assertScaledBigDecimal("15.00", netImpact);
    }

    @Test
    @DisplayName("Waiver Check - Should handle 100% discount as a specific waiver")
    void shouldHandleOneHundredPercentDiscount() {
        List<PriceComponentDetail> components = List.of(
                createComponentDetail("BASE_FEE", "10.00", ValueType.FEE_ABSOLUTE, null),
                createComponentDetail("MAINTENANCE_FEE", "25.00", ValueType.FEE_ABSOLUTE, null),
                createComponentDetail("WAIVE_MAINTENANCE", "100.00", ValueType.DISCOUNT_PERCENTAGE, "MAINTENANCE_FEE")
        );

        // 10.00 (Base) + 25.00 (Maint) - 25.00 (100% Waiver) = 10.00 impact
        BigDecimal netImpact = aggregator.calculateBundleImpact(components, BigDecimal.ZERO, BigDecimal.ZERO, null, LocalDate.now());

        assertScaledBigDecimal("10.00", netImpact);
    }

    @Test
    @DisplayName("Free Count - Should have zero monetary impact")
    void shouldIgnoreFreeCountInCalculation() {
        List<PriceComponentDetail> components = List.of(
                createComponentDetail("FIXED_FEE", "5.00", ValueType.FEE_ABSOLUTE, null),
                createComponentDetail("ATM_ENTITLEMENT", "5.00", ValueType.FREE_COUNT, null)
        );

        BigDecimal netImpact = aggregator.calculateBundleImpact(components, BigDecimal.ZERO, BigDecimal.ZERO, null, LocalDate.now());

        assertScaledBigDecimal("5.00", netImpact);
    }

    @Test
    @DisplayName("Rounding - Should handle fractional percentages correctly (e.g., 1.75%)")
    void shouldHandleFractionalPercentages() {
        // ARRANGE: $100.00 Product Base * 1.75% = $1.75 fee impact
        List<PriceComponentDetail> components = List.of(
                createComponentDetail("FRACTIONAL_FEE", "1.75", ValueType.FEE_PERCENTAGE, null)
        );

        BigDecimal netImpact = aggregator.calculateBundleImpact(components, new BigDecimal("100.00"), BigDecimal.ZERO, null, LocalDate.now());

        assertScaledBigDecimal("1.75", netImpact);
    }

    @Test
    @DisplayName("Aggregate Mix - 10% Global Discount should see Product + Fees")
    void shouldCalculateGlobalDiscountAgainstWholePool() {
        // ARRANGE: $200.00 Product Base
        // + $5.00 Bundle Admin Fee
        // = $205.00 Total Pool
        // - 10% Global Discount = $20.50
        // Expected Net Impact: $5.00 - $20.50 = -$15.50

        List<PriceComponentDetail> components = List.of(
                createComponentDetail("ADMIN_FEE", "5.00", ValueType.FEE_ABSOLUTE, null),
                createComponentDetail("GLOBAL_PROMO", "10.00", ValueType.DISCOUNT_PERCENTAGE, null)
        );

        BigDecimal netImpact = aggregator.calculateBundleImpact(components, new BigDecimal("200.00"), new BigDecimal("200.00"), null, LocalDate.now());

        // Impact on the price is a reduction of $15.50
        assertScaledBigDecimal("-15.50", netImpact);
    }

    // -----------------------------------------------------------------------------------
    // HELPERS
    // -----------------------------------------------------------------------------------

    private void assertScaledBigDecimal(String expected, BigDecimal actual) {
        BigDecimal expectedScaled = new BigDecimal(expected).setScale(SCALE, RoundingMode.HALF_UP);
        assertEquals(expectedScaled, actual, "Math mismatch in price aggregation");
    }

    private PriceComponentDetail createComponentDetail(String code, String amount, ValueType type, String target) {
        return PriceComponentDetail.builder()
                .componentCode(code)
                .rawValue(new BigDecimal(amount))
                .valueType(type)
                .targetComponentCode(target)
                .build();
    }
}