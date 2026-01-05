package com.bankengine.pricing.service;

import com.bankengine.pricing.dto.ProductPricingCalculationResult.PriceComponentDetail;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class PriceAggregator {

    /**
     * Aggregates various price components into a single final price.
     * Logic: Sum(Fees) - Sum(Discounts)
     */
    public BigDecimal calculate(List<PriceComponentDetail> components, BigDecimal transactionAmount) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal amountForCalc = (transactionAmount != null) ? transactionAmount : BigDecimal.ZERO;

        for (PriceComponentDetail detail : components) {
            BigDecimal componentValue = detail.getAmount() != null ? detail.getAmount() : BigDecimal.ZERO;

            switch (detail.getValueType()) {
                case ABSOLUTE ->
                        total = total.add(componentValue);

                case PERCENTAGE ->
                        total = total.add(calculatePercentage(amountForCalc, componentValue));

                case DISCOUNT_ABSOLUTE ->
                        total = total.subtract(componentValue);

                case DISCOUNT_PERCENTAGE ->
                        total = total.subtract(calculatePercentage(amountForCalc, componentValue));

                case WAIVED, FREE_COUNT -> {
                    // These do not impact the monetary total
                }
            }
        }

        // Return 0.00 if the result is negative (a bank typically doesn't pay the customer to take a product)
        return total.compareTo(BigDecimal.ZERO) < 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePercentage(BigDecimal base, BigDecimal rate) {
        return base.multiply(rate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
}