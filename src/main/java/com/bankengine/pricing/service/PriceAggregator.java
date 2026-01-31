package com.bankengine.pricing.service;

import com.bankengine.pricing.dto.PricingRequest;
import com.bankengine.pricing.dto.ProductPricingCalculationResult.PriceComponentDetail;
import com.bankengine.pricing.model.PriceValue;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Component
public class PriceAggregator {

    private static final BigDecimal ZERO_PRECISION = new BigDecimal("0.00");

    public BigDecimal calculate(List<PriceComponentDetail> components, PricingRequest request) {
        BigDecimal totalFees = BigDecimal.ZERO;
        BigDecimal totalDiscounts = BigDecimal.ZERO;
        BigDecimal txAmount = (request.getAmount() != null) ? request.getAmount() : BigDecimal.ZERO;

        // Pass 1: Sum up all Fees (handling Pro-Rata and Breach logic)
        for (PriceComponentDetail detail : components) {
            if (isFee(detail.getValueType())) {
                BigDecimal componentFee = calculateBaseFee(detail, txAmount);

                // DSK Requirement: Pro-rata First Month
                if (detail.isProRataApplicable() && request.getEnrollmentDate() != null) {
                    componentFee = applyProRataLogic(componentFee, request.getEnrollmentDate(), request.getEffectiveDate());
                }

                detail.setCalculatedAmount(componentFee);
                totalFees = totalFees.add(componentFee);
            }
        }

        // Pass 2: Calculate Discounts
        for (PriceComponentDetail detail : components) {
            if (isDiscount(detail.getValueType())) {
                BigDecimal baseForDiscount = determineBaseForDiscount(detail, components, totalFees);
                BigDecimal discountVal = BigDecimal.ZERO;

                if (detail.getValueType() == PriceValue.ValueType.DISCOUNT_PERCENTAGE) {
                    discountVal = baseForDiscount.multiply(getRaw(detail))
                            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                } else {
                    discountVal = getRaw(detail).abs();
                }

                detail.setCalculatedAmount(discountVal.negate());
                totalDiscounts = totalDiscounts.add(discountVal);
            }
        }

        BigDecimal result = totalFees.subtract(totalDiscounts);

        if (result.compareTo(BigDecimal.ZERO) < 0) {
            return ZERO_PRECISION;
        }

        return result.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateBaseFee(PriceComponentDetail detail, BigDecimal txAmount) {
        if (detail.getValueType() == PriceValue.ValueType.FEE_PERCENTAGE) {
            return txAmount.multiply(getRaw(detail))
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }
        return getRaw(detail);
    }

    private BigDecimal applyProRataLogic(BigDecimal fullFee, LocalDate enrollmentDate, LocalDate effectiveDate) {
        // Defensive: default to now if effectiveDate is missing in request
        LocalDate referenceDate = (effectiveDate != null) ? effectiveDate : LocalDate.now();

        if (enrollmentDate.getMonth() != referenceDate.getMonth() || enrollmentDate.getYear() != referenceDate.getYear()) {
            return fullFee;
        }

        int daysInMonth = enrollmentDate.lengthOfMonth();
        int activeDays = daysInMonth - enrollmentDate.getDayOfMonth() + 1;

        return fullFee.multiply(BigDecimal.valueOf(activeDays))
                .divide(BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal determineBaseForDiscount(PriceComponentDetail discount, List<PriceComponentDetail> allComponents, BigDecimal totalFees) {
        String target = discount.getTargetComponentCode();
        if (target == null || target.isBlank()) {
            return totalFees;
        }
        return allComponents.stream()
                .filter(c -> isFee(c.getValueType()) && target.equals(c.getComponentCode()))
                .map(c -> c.getCalculatedAmount() != null ? c.getCalculatedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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