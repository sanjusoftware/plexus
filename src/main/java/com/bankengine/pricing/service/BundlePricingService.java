package com.bankengine.pricing.service;

import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.repository.ProductBundleRepository;
import com.bankengine.common.service.BaseService;
import com.bankengine.pricing.dto.BundlePriceRequest;
import com.bankengine.pricing.dto.BundlePriceResponse;
import com.bankengine.pricing.dto.BundlePriceResponse.ProductPricingResult;
import com.bankengine.pricing.dto.PricingRequest;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.dto.ProductPricingCalculationResult.PriceComponentDetail;
import com.bankengine.pricing.model.BundlePricingLink;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.rules.model.BundlePricingInput;
import com.bankengine.rules.service.BundleRulesEngineService;
import com.bankengine.web.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BundlePricingService extends BaseService {

    private final PricingCalculationService pricingCalculationService;
    private final BundleRulesEngineService bundleRulesEngineService;
    private final ProductBundleRepository productBundleRepository;

    /**
     * Calculates the total price for a bundle by:
     * 1. Calculating individual product prices.
     * 2. Applying fixed bundle-level adjustments from the database.
     * 3. Applying dynamic bundle-level adjustments from the rules engine (Drools).
     */
    @Transactional(readOnly = true)
    public BundlePriceResponse calculateTotalBundlePrice(BundlePriceRequest request) {

        if (request.getProducts() == null || request.getProducts().isEmpty()) {
            throw new NotFoundException("The bundle must contain at least one product.");
        }

        // 1. Calculate Individual Product Prices
        List<ProductPricingResult> productResults = new ArrayList<>();
        BigDecimal productGrossTotal = BigDecimal.ZERO;

        for (BundlePriceRequest.ProductRequest productReq : request.getProducts()) {
            PricingRequest singlePricingRequest = PricingRequest.builder()
                    .productId(productReq.getProductId())
                    .amount(productReq.getAmount())
                    .customerSegment(request.getCustomerSegment())
                    .effectiveDate(request.getEffectiveDate())
                    .build();

            ProductPricingCalculationResult result = pricingCalculationService.getProductPricing(singlePricingRequest);

            BigDecimal productTotal = result.getFinalChargeablePrice();
            productGrossTotal = productGrossTotal.add(productTotal);

            productResults.add(ProductPricingResult.builder()
                    .productId(productReq.getProductId())
                    .productTotalAmount(productTotal)
                    .pricingComponents(result.getComponentBreakdown())
                    .build());
        }

        // 2. Fetch the Bundle Securely (Validates existence and tenant ownership)
        ProductBundle bundle = productBundleRepository.findById(request.getProductBundleId())
                .orElseThrow(() -> new NotFoundException("Product Bundle not found with ID: " + request.getProductBundleId()));

        List<PriceComponentDetail> bundleAdjustments = new ArrayList<>();

        // This effectively final variable is required for lambda capturing
        final BigDecimal finalProductGross = productGrossTotal;

        // 3. Process Fixed Adjustments (BundlePricingLink)
        if (bundle.getBundlePricingLinks() != null) {
            bundle.getBundlePricingLinks().stream()
                    .filter(link -> !link.isUseRulesEngine() && link.getFixedValue() != null)
                    .forEach(link -> {
                        PriceValue.ValueType type = link.getFixedValueType() != null ?
                                link.getFixedValueType() : PriceValue.ValueType.FEE_ABSOLUTE;

                        bundleAdjustments.add(PriceComponentDetail.builder()
                                .componentCode(link.getPricingComponent().getName())
                                .rawValue(link.getFixedValue())
                                .calculatedAmount(calculateSignedAmount(link.getFixedValue(), type, finalProductGross))
                                .valueType(type)
                                .sourceType("FIXED_VALUE")
                                .build());
                    });
        }

        // 4. Process Dynamic Adjustments from Rules Engine (Drools)
        BundlePricingInput bundleInputFact = new BundlePricingInput();
        bundleInputFact.setGrossTotalAmount(finalProductGross);
        bundleInputFact.setBankId(getCurrentBankId());
        bundleInputFact.setCustomerSegment(request.getCustomerSegment());

        // Provide context for rules that check for specific products within the bundle
        bundleInputFact.setContainedProductIds(bundle.getContainedProducts().stream()
                .map(link -> link.getProduct().getId())
                .collect(Collectors.toList()));

        // IMPORTANT: Whitelist only components linked to this bundle for the rules engine
        Set<Long> targetComponentIds = bundle.getBundlePricingLinks().stream()
                .filter(BundlePricingLink::isUseRulesEngine)
                .map(link -> link.getPricingComponent().getId())
                .collect(Collectors.toSet());
        bundleInputFact.setTargetPricingComponentIds(targetComponentIds);

        // Populate Custom Attributes for LHS matching in DRL
        bundleInputFact.getCustomAttributes().put("customerSegment", request.getCustomerSegment());
        bundleInputFact.getCustomAttributes().put("transactionAmount", finalProductGross);
        if (request.getCustomAttributes() != null) {
            bundleInputFact.getCustomAttributes().putAll(request.getCustomAttributes());
        }

        // Fire Rules
        BundlePricingInput adjustedFact = bundleRulesEngineService.determineBundleAdjustments(bundleInputFact);
        bundleAdjustments.addAll(convertRulesToDetail(adjustedFact.getAdjustments(), finalProductGross));

        // 5. Aggregate Final Amounts
        // Final Gross = Sum(Product Prices) + Sum(Bundle Fees)
        BigDecimal totalBundleFees = bundleAdjustments.stream()
                .filter(adj -> isFee(adj.getValueType()))
                .map(PriceComponentDetail::getCalculatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Total Discounts (Calculated as negative values)
        BigDecimal totalBundleDiscounts = bundleAdjustments.stream()
                .filter(adj -> isDiscount(adj.getValueType()))
                .map(PriceComponentDetail::getCalculatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Bundle Gross = Products + Bundle Fees
        BigDecimal totalGross = finalProductGross.add(totalBundleFees);

        return BundlePriceResponse.builder()
                .productBundleId(request.getProductBundleId())
                .grossTotalAmount(totalGross)
                .bundleAdjustments(bundleAdjustments)
                .productResults(productResults)
                .netTotalAmount(totalGross.add(totalBundleDiscounts)) // Discounts are negative
                .build();
    }

    /**
     * Converts structured BundleAdjustment facts into UI-friendly details.
     */
    private List<PriceComponentDetail> convertRulesToDetail(Map<String, BundlePricingInput.BundleAdjustment> adjustments, BigDecimal grossTotal) {
        if (adjustments == null) return new ArrayList<>();

        return adjustments.entrySet().stream().map(entry -> {
            PriceValue.ValueType type = PriceValue.ValueType.valueOf(entry.getValue().getType());
            BigDecimal rawValue = entry.getValue().getValue();

            return PriceComponentDetail.builder()
                    .componentCode(entry.getKey())
                    .rawValue(rawValue)
                    .calculatedAmount(calculateSignedAmount(rawValue, type, grossTotal))
                    .sourceType("BUNDLE_RULES")
                    .valueType(type)
                    .build();
        }).toList();
    }

    /**
     * Common logic to ensure discounts are returned as negative numbers
     * and fees as positive, regardless of input sign.
     */
    private BigDecimal calculateSignedAmount(BigDecimal val, PriceValue.ValueType type, BigDecimal base) {
        BigDecimal absoluteVal = val.abs();
        BigDecimal amount = switch (type) {
            case DISCOUNT_PERCENTAGE, FEE_PERCENTAGE -> base.multiply(absoluteVal)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            default -> absoluteVal;
        };
        return isDiscount(type) ? amount.negate() : amount;
    }

    private boolean isDiscount(PriceValue.ValueType type) {
        return type == PriceValue.ValueType.DISCOUNT_ABSOLUTE || type == PriceValue.ValueType.DISCOUNT_PERCENTAGE;
    }

    private boolean isFee(PriceValue.ValueType type) {
        return type == PriceValue.ValueType.FEE_ABSOLUTE || type == PriceValue.ValueType.FEE_PERCENTAGE;
    }
}