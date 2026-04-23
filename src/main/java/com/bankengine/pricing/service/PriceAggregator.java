package com.bankengine.pricing.service;

import com.bankengine.pricing.dto.ProductPricingCalculationResult.PriceComponentDetail;
import com.bankengine.pricing.model.PriceValue;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PriceAggregator {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;
    private static final int INTERNAL_SCALE = 6;

    private record BillingCycle(LocalDate referenceDate, LocalDate startDate, LocalDate endDate, int totalDays) {
        private static BillingCycle from(LocalDate effectiveDate) {
            LocalDate reference = effectiveDate != null ? effectiveDate : LocalDate.now();
            return new BillingCycle(
                    reference,
                    reference.withDayOfMonth(1),
                    reference.withDayOfMonth(reference.lengthOfMonth()),
                    reference.lengthOfMonth()
            );
        }
    }

    private record ActiveWindow(LocalDate startDate, LocalDate endDate, int activeDays) {
        private static ActiveWindow empty() {
            return new ActiveWindow(null, null, 0);
        }

        private int overlapDays(ActiveWindow other) {
            if (other == null || this.activeDays <= 0 || other.activeDays <= 0) {
                return 0;
            }

            LocalDate overlapStart = startDate.isAfter(other.startDate) ? startDate : other.startDate;
            LocalDate overlapEnd = endDate.isBefore(other.endDate) ? endDate : other.endDate;
            if (overlapEnd.isBefore(overlapStart)) {
                return 0;
            }
            return Math.toIntExact(ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1);
        }
    }

    private record ComponentComputation(BigDecimal fullCycleAmount, ActiveWindow activeWindow, boolean isProRata) {
        private BigDecimal amountForOverlap(ActiveWindow discountWindow, int cycleDays) {
            if (!isProRata) {
                return fullCycleAmount.setScale(SCALE, RoundingMode.HALF_UP);
            }
            int overlapDays = activeWindow.overlapDays(discountWindow);
            return prorateAmount(fullCycleAmount, overlapDays, cycleDays);
        }
    }

    private record FeeCalculationResult(BigDecimal totalFees, Map<String, List<ComponentComputation>> feeContexts) {}

    private record DiscountCalculation(BigDecimal amount, BigDecimal capPool) {}

    public BigDecimal calculateBundleImpact(List<PriceComponentDetail> components,
                                            BigDecimal principalAmount,
                                            BigDecimal existingFeePool,
                                            LocalDate enrollmentDate,
                                            LocalDate effectiveDate) {

        BillingCycle billingCycle = BillingCycle.from(effectiveDate);

        components.forEach(c -> c.setCalculatedAmount(BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP)));

        FeeCalculationResult feeCalculation = sumBundleLevelFees(components, principalAmount, enrollmentDate, billingCycle);

        BigDecimal totalBundleDiscounts = sumBundleLevelDiscounts(
                components,
                existingFeePool,
                feeCalculation,
                enrollmentDate,
                billingCycle
        );

        return feeCalculation.totalFees().subtract(totalBundleDiscounts).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private FeeCalculationResult sumBundleLevelFees(List<PriceComponentDetail> components,
                                                    BigDecimal principalAmount,
                                                    LocalDate enrollmentDate,
                                                    BillingCycle billingCycle) {
        BigDecimal total = BigDecimal.ZERO;
        Map<String, List<ComponentComputation>> feeContexts = new HashMap<>();

        for (PriceComponentDetail detail : components) {
            if (isFee(detail.getValueType())) {
                BigDecimal fullCycleFee = calculateComponentValue(detail, principalAmount);
                ActiveWindow activeWindow = resolveActiveWindow(detail, enrollmentDate, billingCycle);
                boolean prorateFlag = shouldProrate(detail);

                applyProRataMetadata(detail, activeWindow, billingCycle, prorateFlag);

                BigDecimal fee = prorateFlag
                        ? prorateAmount(fullCycleFee, activeWindow.activeDays(), billingCycle.totalDays())
                        : fullCycleFee.setScale(SCALE, RoundingMode.HALF_UP);

                BigDecimal scaledFee = fee.setScale(SCALE, RoundingMode.HALF_UP).abs();
                detail.setCalculatedAmount(scaledFee);
                total = total.add(scaledFee);

                feeContexts.computeIfAbsent(detail.getComponentCode(), ignored -> new ArrayList<>())
                        .add(new ComponentComputation(fullCycleFee.abs(), activeWindow, prorateFlag));
            }
        }
        return new FeeCalculationResult(total, feeContexts);
    }

    private BigDecimal sumBundleLevelDiscounts(List<PriceComponentDetail> components,
                                               BigDecimal existingFeePool,
                                               FeeCalculationResult feeCalculation,
                                               LocalDate enrollmentDate,
                                               BillingCycle billingCycle) {
        BigDecimal total = BigDecimal.ZERO;
        for (PriceComponentDetail detail : components) {
            if (isDiscount(detail.getValueType())) {
                DiscountCalculation discountCalculation = calculateDiscountAmount(
                        detail,
                        existingFeePool,
                        feeCalculation.feeContexts(),
                        enrollmentDate,
                        billingCycle
                );
                BigDecimal discountAmount = discountCalculation.amount().min(discountCalculation.capPool());

                BigDecimal scaledDiscount = discountAmount.setScale(SCALE, RoundingMode.HALF_UP).negate();
                detail.setCalculatedAmount(scaledDiscount);
                total = total.add(discountAmount);
            }
        }
        return total;
    }

    private DiscountCalculation calculateDiscountAmount(PriceComponentDetail detail,
                                                        BigDecimal existingFeePool,
                                                        Map<String, List<ComponentComputation>> feeContexts,
                                                        LocalDate enrollmentDate,
                                                        BillingCycle billingCycle) {
        ActiveWindow discountWindow = resolveActiveWindow(detail, enrollmentDate, billingCycle);
        boolean prorateFlag = shouldProrate(detail);

        applyProRataMetadata(detail, discountWindow, billingCycle, prorateFlag);
        BigDecimal capPool = determineInternalDiscountPool(detail, feeContexts, discountWindow, billingCycle);

        if (!hasTarget(detail)) {
            BigDecimal externalFeePool = prorateFlag
                    ? prorateAmount(existingFeePool, discountWindow.activeDays(), billingCycle.totalDays())
                    : scaleCurrency(existingFeePool);
            capPool = capPool.add(externalFeePool);
        }

        if (detail.getValueType() == PriceValue.ValueType.DISCOUNT_PERCENTAGE) {
            return new DiscountCalculation(percentageOf(capPool, getRaw(detail)), capPool);
        }

        int discountDays = prorateFlag
                ? (hasTarget(detail) ? resolveTargetOverlapDays(detail, feeContexts, discountWindow) : discountWindow.activeDays())
                : billingCycle.totalDays();

        BigDecimal absoluteDiscount = prorateFlag
                ? prorateAmount(getRaw(detail).abs(), discountDays, billingCycle.totalDays())
                : getRaw(detail).abs().setScale(SCALE, RoundingMode.HALF_UP);

        return new DiscountCalculation(absoluteDiscount, capPool);
    }

    private BigDecimal calculateComponentValue(PriceComponentDetail detail, BigDecimal base) {
        if (detail.getValueType() == PriceValue.ValueType.FEE_PERCENTAGE ||
                detail.getValueType() == PriceValue.ValueType.DISCOUNT_PERCENTAGE) {
            return percentageOf(base, getRaw(detail));
        }
        return getRaw(detail).abs();
    }

    private BigDecimal determineInternalDiscountPool(PriceComponentDetail detail,
                                                     Map<String, List<ComponentComputation>> feeContexts,
                                                     ActiveWindow discountWindow,
                                                     BillingCycle billingCycle) {
        if (hasTarget(detail)) {
            return feeContexts.getOrDefault(detail.getTargetComponentCode(), List.of()).stream()
                    .map(context -> context.amountForOverlap(discountWindow, billingCycle.totalDays()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        return feeContexts.values().stream()
                .flatMap(List::stream)
                .map(context -> context.amountForOverlap(discountWindow, billingCycle.totalDays()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int resolveTargetOverlapDays(PriceComponentDetail detail,
                                         Map<String, List<ComponentComputation>> feeContexts,
                                         ActiveWindow discountWindow) {
        return feeContexts.getOrDefault(detail.getTargetComponentCode(), List.of()).stream()
                .mapToInt(context -> context.activeWindow().overlapDays(discountWindow))
                .max()
                .orElse(0);
    }

    private ActiveWindow resolveActiveWindow(PriceComponentDetail detail,
                                             LocalDate enrollmentDate,
                                             BillingCycle billingCycle) {
        LocalDate activeStart = billingCycle.startDate();
        if (enrollmentDate != null && enrollmentDate.isAfter(activeStart)) {
            activeStart = enrollmentDate;
        }
        if (detail.getEffectiveDate() != null && detail.getEffectiveDate().isAfter(activeStart)) {
            activeStart = detail.getEffectiveDate();
        }

        LocalDate activeEnd = billingCycle.endDate();
        if (detail.getExpiryDate() != null && detail.getExpiryDate().isBefore(activeEnd)) {
            activeEnd = detail.getExpiryDate();
        }

        if (activeEnd.isBefore(activeStart)) {
            return ActiveWindow.empty();
        }

        int activeDays = Math.toIntExact(ChronoUnit.DAYS.between(activeStart, activeEnd) + 1);
        return new ActiveWindow(activeStart, activeEnd, activeDays);
    }

    private static BigDecimal prorateAmount(BigDecimal amount, int activeDays, int cycleDays) {
        if (amount == null || amount.signum() == 0 || activeDays <= 0 || cycleDays <= 0) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal dailyRate = amount.abs().divide(BigDecimal.valueOf(cycleDays), INTERNAL_SCALE, RoundingMode.HALF_UP);
        return dailyRate.multiply(BigDecimal.valueOf(activeDays)).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal percentageOf(BigDecimal base, BigDecimal percent) {
        return base.multiply(percent).divide(HUNDRED, INTERNAL_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleCurrency(BigDecimal amount) {
        return amount == null
                ? BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP)
                : amount.abs().setScale(SCALE, RoundingMode.HALF_UP);
    }

    private boolean shouldProrate(PriceComponentDetail detail) {
        return detail.isProRataApplicable();
    }

    private boolean hasTarget(PriceComponentDetail detail) {
        return detail.getTargetComponentCode() != null && !detail.getTargetComponentCode().isBlank();
    }

    private void applyProRataMetadata(PriceComponentDetail detail,
                                      ActiveWindow activeWindow,
                                      BillingCycle billingCycle,
                                      boolean prorated) {
        // ALWAYS set the days based on the window if dates are provided.
        // This ensures the response/test sees the actual days active (e.g., 16),
        // even if 'prorated' flag is false and we aren't scaling the dollar value.
        detail.setActiveDays(activeWindow.activeDays() > 0 ? activeWindow.activeDays() : billingCycle.totalDays());
        detail.setBillingCycleDays(billingCycle.totalDays());
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