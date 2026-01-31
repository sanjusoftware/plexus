package com.bankengine.pricing.service;

import com.bankengine.catalog.repository.ProductBundleRepository;
import com.bankengine.common.exception.ValidationException;
import com.bankengine.common.service.BaseService;
import com.bankengine.pricing.dto.BundlePriceRequest;
import com.bankengine.pricing.dto.BundlePriceResponse;
import com.bankengine.pricing.dto.BundlePriceResponse.ProductPricingResult;
import com.bankengine.pricing.dto.PricingRequest;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.dto.ProductPricingCalculationResult.PriceComponentDetail;
import com.bankengine.pricing.model.BundlePricingLink;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.repository.BundlePricingLinkRepository;
import com.bankengine.rules.model.BundlePricingInput;
import com.bankengine.rules.service.BundleRulesEngineService;
import com.bankengine.web.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BundlePricingService extends BaseService {

    private final PricingCalculationService pricingCalculationService;
    private final BundleRulesEngineService bundleRulesEngineService;
    private final ProductBundleRepository productBundleRepository;
    private final BundlePricingLinkRepository bundlePricingLinkRepository;

    /**
     * Calculates the total price for a bundle by:
     * 1. Validating input and calculating individual product prices.
     * 2. Fetching active temporal links for bundle-level adjustments.
     * 3. Applying fixed bundle-level adjustments from the database.
     * 4. Applying dynamic bundle-level adjustments from the rules engine (Drools).
     */
    @Transactional(readOnly = true)
    public BundlePriceResponse calculateTotalBundlePrice(BundlePriceRequest request) {

        // 0. Fail Fast: Validate input presence
        if (request.getProducts() == null || request.getProducts().isEmpty()) {
            throw new ValidationException("Bundle calculation requires at least one product.");
        }

        // 1. Calculate Individual Product Prices
        // We aggregate the prices of all products in the bundle as the "base" for adjustments
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

        // 2. Fetch Bundle and Active Temporal Links
        // Verify bundle existence first
        productBundleRepository.findById(request.getProductBundleId())
                .orElseThrow(() -> new NotFoundException("Product Bundle not found with ID: " + request.getProductBundleId()));

        // Retrieve only the links that are active for the requested effectiveDate
        List<BundlePricingLink> activeBundleLinks = bundlePricingLinkRepository
                .findByBundleIdAndDate(request.getProductBundleId(), request.getEffectiveDate());

        if (activeBundleLinks.isEmpty()) {
            log.debug("No active bundle-level adjustments for bundle ID: {} on date: {}",
                    request.getProductBundleId(), request.getEffectiveDate());
            // We proceed with empty adjustments; the price is simply the sum of products
        }

        List<PriceComponentDetail> bundleAdjustments = new ArrayList<>();
        final BigDecimal finalProductGross = productGrossTotal;

        // 3. Process Fixed Adjustments
        // These are static fees or discounts defined directly on the link
        activeBundleLinks.stream()
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

        // 4. Process Dynamic Adjustments from Rules Engine (Drools)
        BundlePricingInput bundleInputFact = new BundlePricingInput();
        bundleInputFact.setGrossTotalAmount(finalProductGross);
        bundleInputFact.setBankId(getCurrentBankId());
        bundleInputFact.setCustomerSegment(request.getCustomerSegment());

        // Ensure Rules Engine evaluates based on the requested effective date
        bundleInputFact.setReferenceDate(request.getEffectiveDate());

        // Target only the components that the database says are active and rules-based
        Set<Long> targetComponentIds = activeBundleLinks.stream()
                .filter(BundlePricingLink::isUseRulesEngine)
                .map(link -> link.getPricingComponent().getId())
                .collect(Collectors.toSet());
        bundleInputFact.setTargetPricingComponentIds(targetComponentIds);

        // Populate Custom Attributes for Rule LHS (Left Hand Side) matching
        bundleInputFact.getCustomAttributes().put("customerSegment", request.getCustomerSegment());
        bundleInputFact.getCustomAttributes().put("transactionAmount", finalProductGross);
        bundleInputFact.getCustomAttributes().put("effectiveDate", request.getEffectiveDate());
        if (request.getCustomAttributes() != null) {
            bundleInputFact.getCustomAttributes().putAll(request.getCustomAttributes());
        }

        // Fire Drools rules and convert the resulting adjustments into response details
        BundlePricingInput adjustedFact = bundleRulesEngineService.determineBundleAdjustments(bundleInputFact);
        bundleAdjustments.addAll(convertRulesToDetail(adjustedFact.getAdjustments(), finalProductGross));

        // 5. Aggregate Final Amounts
        // Bundle Fees (Positive) increase the total; Discounts (Negative) decrease it
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
                .netTotalAmount(totalGross.add(totalBundleDiscounts))
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