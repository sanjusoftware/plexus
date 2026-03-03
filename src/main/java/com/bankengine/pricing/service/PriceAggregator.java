package com.bankengine.pricing.service;

import com.bankengine.pricing.dto.ProductPricingCalculationResult.PriceComponentDetail;
import com.bankengine.pricing.model.PriceValue;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Component
public class PriceAggregator {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;

    public BigDecimal calculateBundleImpact(List<PriceComponentDetail> components,
                                            BigDecimal principalAmount,
                                            BigDecimal existingFeePool,
                                            LocalDate enrollmentDate,
                                            LocalDate effectiveDate) {

        components.forEach(c -> c.setCalculatedAmount(BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP)));

        // 1. Fees are calculated based on the 'principalAmount' ($1000)
        BigDecimal totalBundleFees = sumBundleLevelFees(components, principalAmount,
                enrollmentDate, effectiveDate);

        // 2. Discounts target the 'existingFeePool' ($15) + the new fees ($10)
        BigDecimal totalBundleDiscounts = sumBundleLevelDiscounts(components, existingFeePool, totalBundleFees);

        // 4. Return the net change to the price
        return totalBundleFees.subtract(totalBundleDiscounts).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal sumBundleLevelFees(List<PriceComponentDetail> components,
                                          BigDecimal principalAmount,
                                          LocalDate enroll, LocalDate effect) {
        BigDecimal total = BigDecimal.ZERO;
        for (PriceComponentDetail detail : components) {
            if (isFee(detail.getValueType())) {
                BigDecimal fee = calculateComponentValue(detail, principalAmount);

                if (detail.isProRataApplicable() && enroll != null) {
                    fee = applyProRataLogic(fee, enroll, effect);
                }

                BigDecimal scaledFee = fee.setScale(SCALE, RoundingMode.HALF_UP);
                detail.setCalculatedAmount(scaledFee);
                total = total.add(scaledFee);
            }
        }
        return total;
    }

    private BigDecimal sumBundleLevelDiscounts(List<PriceComponentDetail> components,
                                               BigDecimal existingFeePool,
                                               BigDecimal totalBundleFees) {
        BigDecimal total = BigDecimal.ZERO;
        for (PriceComponentDetail detail : components) {
            if (isDiscount(detail.getValueType())) {
                BigDecimal discountPool = determineDiscountPool(detail, components, existingFeePool, totalBundleFees);
                BigDecimal discountAmount = calculateComponentValue(detail, discountPool);

                // Cap the discount so it doesn't exceed the targeted pool
                discountAmount = discountAmount.min(discountPool);

                // Store as negative for the breakdown
                BigDecimal scaledDiscount = discountAmount.setScale(SCALE, RoundingMode.HALF_UP).negate();
                detail.setCalculatedAmount(scaledDiscount);
                total = total.add(discountAmount); // We add the positive amount to subtract later in main method
            }
        }
        return total;
    }

    private BigDecimal determineDiscountPool(PriceComponentDetail priceComponentDetail,
                                             List<PriceComponentDetail> all,
                                             BigDecimal existingFeePool,
                                             BigDecimal bundleFees) {
        String target = priceComponentDetail.getTargetComponentCode();

        // GLOBAL DISCOUNT: Sum of external fees (existingFeePool) + new fees (bundleFees)
        if (target == null || target.isBlank()) {
            return existingFeePool.add(bundleFees);
        }

        // Targeted discount: finds the specific component it is meant to offset
        return all.stream()
                .filter(c -> target.equals(c.getComponentCode()))
                .map(c -> c.getCalculatedAmount() != null ? c.getCalculatedAmount() : BigDecimal.ZERO)
                .map(BigDecimal::abs) // Pool is always a positive magnitude
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateComponentValue(PriceComponentDetail detail, BigDecimal base) {
        if (detail.getValueType() == PriceValue.ValueType.FEE_PERCENTAGE ||
                detail.getValueType() == PriceValue.ValueType.DISCOUNT_PERCENTAGE) {
            return base.multiply(getRaw(detail)).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        }
        return getRaw(detail).abs();
    }

    private BigDecimal applyProRataLogic(BigDecimal fullFee, LocalDate enrollmentDate, LocalDate effectiveDate) {
        // default to now if effectiveDate is missing in request
        LocalDate referenceDate = (effectiveDate != null) ? effectiveDate : LocalDate.now();
        if (enrollmentDate.getMonth() != referenceDate.getMonth() || enrollmentDate.getYear() != referenceDate.getYear()) {
            return fullFee;
        }

        int daysInMonth = enrollmentDate.lengthOfMonth();
        int activeDays = daysInMonth - enrollmentDate.getDayOfMonth() + 1;
        return fullFee.multiply(BigDecimal.valueOf(activeDays))
                .divide(BigDecimal.valueOf(daysInMonth), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal getRaw(PriceComponentDetail detail) {
        return detail.getRawValue() != null ? detail.getRawValue() : BigDecimal.ZERO;
    }

    private boolean isFee(PriceValue.ValueType type) {
        return type == PriceValue.ValueType.FEE_ABSOLUTE || type == PriceValue.ValueType.FEE_PERCENTAGE;
    }

    private boolean isDiscount(PriceValue.ValueType type) {
        return type == PriceValue.ValueType.DISCOUNT_ABSOLUTE || type == PriceValue.ValueType.DISCOUNT_PERCENTAGE;
    }
}