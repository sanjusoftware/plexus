package com.bankengine.pricing.service;

import com.bankengine.pricing.dto.ProductPricingCalculationResult.PriceComponentDetail;
import com.bankengine.pricing.model.PriceValue;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class PriceAggregator {

    /**
     * Aggregates various price components into a single final price.
     * Logic:
     * 1. Sum(Calculated Fees)
     * 2. Calculate Discounts (Absolute + % of Total Fees)
     * 3. Result = Fees - Discounts (Floor 0)
     */
    public BigDecimal calculate(List<PriceComponentDetail> components, BigDecimal transactionAmount) {
        BigDecimal totalFees = BigDecimal.ZERO;
        BigDecimal totalDiscounts = BigDecimal.ZERO;

        // Ensure we don't NPE on null amount
        BigDecimal baseAmount = (transactionAmount != null) ? transactionAmount : BigDecimal.ZERO;

        // Pass 1: Sum up all Fees
        for (PriceComponentDetail detail : components) {
            if (detail.getValueType() == PriceValue.ValueType.FEE_ABSOLUTE) {
                BigDecimal amt = getRaw(detail);
                detail.setCalculatedAmount(amt);
                totalFees = totalFees.add(amt);
            } else if (detail.getValueType() == PriceValue.ValueType.FEE_PERCENTAGE) {
                // SINGLE PLACE FOR FEE PERCENTAGE MATH
                BigDecimal calculatedFee = baseAmount.multiply(getRaw(detail))
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                detail.setCalculatedAmount(calculatedFee);
                totalFees = totalFees.add(calculatedFee);
            }
        }

        // Pass 2: Calculate Discounts
        for (PriceComponentDetail detail : components) {
            if (detail.getValueType() == PriceValue.ValueType.DISCOUNT_ABSOLUTE) {
                BigDecimal absDiscount = getRaw(detail).abs();
                detail.setCalculatedAmount(absDiscount.negate());
                totalDiscounts = totalDiscounts.add(absDiscount);
            } else if (detail.getValueType() == PriceValue.ValueType.DISCOUNT_PERCENTAGE) {
                // Determine if we apply % to specific fees or the whole transaction
                BigDecimal baseForDiscount = determineBaseAmount(detail, components, baseAmount);

                // SINGLE PLACE FOR DISCOUNT PERCENTAGE MATH
                BigDecimal discountVal = baseForDiscount.multiply(getRaw(detail))
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

                detail.setCalculatedAmount(discountVal.negate());
                totalDiscounts = totalDiscounts.add(discountVal);
            }
        }

        BigDecimal finalPrice = totalFees.subtract(totalDiscounts);
        return finalPrice.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : finalPrice.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal getRaw(PriceComponentDetail detail) {
        return detail.getRawValue() != null ? detail.getRawValue() : BigDecimal.ZERO;
    }

    /**
     * If targetComponentCode is present, sum only those specific fees.
     * Otherwise, return the totalFees of the entire product/bundle.
     */
    private BigDecimal determineBaseAmount(PriceComponentDetail discount, List<PriceComponentDetail> allComponents, BigDecimal transactionAmount) {
        String target = discount.getTargetComponentCode();

        // IF NO TARGET: Apply discount to the SUM OF FEES
        if (target == null || target.isBlank()) {
            return allComponents.stream()
                    .filter(c -> isFee(c.getValueType()))
                    .map(this::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        // Target found: Filter components matching the code and sum their amounts
        return allComponents.stream()
                .filter(c -> isFee(c.getValueType()) && target.equals(c.getComponentCode()))
                .map(this::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal getAmount(PriceComponentDetail detail) {
        if (detail.getCalculatedAmount() != null) return detail.getCalculatedAmount();
        return detail.getRawValue() != null ? detail.getRawValue() : BigDecimal.ZERO;
    }

    private boolean isFee(PriceValue.ValueType type) {
        return type == PriceValue.ValueType.FEE_ABSOLUTE || type == PriceValue.ValueType.FEE_PERCENTAGE;
    }
}