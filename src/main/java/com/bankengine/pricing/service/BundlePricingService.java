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
     * 3. Applying dynamic bundle-level adjustments from the rules engine.
     */
    @Transactional(readOnly = true)
    public BundlePriceResponse calculateTotalBundlePrice(BundlePriceRequest request) {

        if (request.getProducts() == null || request.getProducts().isEmpty()) {
            throw new NotFoundException("The bundle must contain at least one product.");
        }

        // 1. Calculate Individual Product Prices
        List<ProductPricingResult> productResults = new ArrayList<>();
        BigDecimal grossTotal = BigDecimal.ZERO;

        for (BundlePriceRequest.ProductRequest productReq : request.getProducts()) {
            PricingRequest singlePricingRequest = PricingRequest.builder()
                    .productId(productReq.getProductId())
                    .amount(productReq.getAmount())
                    .customerSegment(request.getCustomerSegment())
                    .effectiveDate(request.getEffectiveDate())
                    .build();

            ProductPricingCalculationResult result = pricingCalculationService.getProductPricing(singlePricingRequest);

            BigDecimal productTotal = result.getFinalChargeablePrice();
            grossTotal = grossTotal.add(productTotal);

            productResults.add(ProductPricingResult.builder()
                    .productId(productReq.getProductId())
                    .productTotalAmount(productTotal)
                    .pricingComponents(result.getComponentBreakdown())
                    .build());
        }

        // 2. Fetch the Bundle and prepare Adjustment List
        ProductBundle bundle = productBundleRepository.findById(request.getProductBundleId())
                .orElseThrow(() -> new NotFoundException("Product Bundle not found with ID: " + request.getProductBundleId()));

        List<PriceComponentDetail> bundleAdjustments = new ArrayList<>();

        // 3. Process Fixed Adjustments (BundlePricingLink)
        if (bundle.getBundlePricingLinks() != null) {
            bundle.getBundlePricingLinks().stream()
                .filter(link -> !link.isUseRulesEngine() && link.getFixedValue() != null)
                .forEach(link -> {
                    // Determine type: if negative and type null, assume DISCOUNT_ABSOLUTE
                    PriceValue.ValueType resolvedType = link.getFixedValueType();
                    if (resolvedType == null) {
                        resolvedType = link.getFixedValue().compareTo(BigDecimal.ZERO) < 0
                                ? PriceValue.ValueType.DISCOUNT_ABSOLUTE
                                : PriceValue.ValueType.FEE_ABSOLUTE;
                    }

                    bundleAdjustments.add(PriceComponentDetail.builder()
                        .componentCode(link.getPricingComponent().getName())
                        .rawValue(link.getFixedValue())
                        .calculatedAmount(link.getFixedValue())
                        .valueType(resolvedType)
                        .sourceType("FIXED_VALUE")
                        .build());
                });
        }

        // 4. Process Dynamic Adjustments from Rules Engine (Drools)
        BundlePricingInput bundleInputFact = new BundlePricingInput();
        bundleInputFact.setBankId(getCurrentBankId());
        bundleInputFact.setCustomerSegment(request.getCustomerSegment());
        bundleInputFact.setGrossTotalAmount(grossTotal);

        // Contextual awareness for Bundle rules
        bundleInputFact.setContainedProductIds(bundle.getContainedProducts().stream()
                .map(link -> link.getProduct().getId())
                .collect(Collectors.toList()));

        // Populate targeted IDs so the "contains" check in DRL works
        Set<Long> targetComponentIds = bundle.getBundlePricingLinks().stream()
                .filter(BundlePricingLink::isUseRulesEngine)
                .map(link -> link.getPricingComponent().getId())
                .collect(Collectors.toSet());
        bundleInputFact.setTargetPricingComponentIds(targetComponentIds);

        // Populate Custom Attributes for LHS matching
        bundleInputFact.getCustomAttributes().put("customerSegment", request.getCustomerSegment());
        bundleInputFact.getCustomAttributes().put("transactionAmount", grossTotal);
        if (request.getCustomAttributes() != null) {
            bundleInputFact.getCustomAttributes().putAll(request.getCustomAttributes());
        }

        BundlePricingInput adjustedFact = bundleRulesEngineService.determineBundleAdjustments(bundleInputFact);
        bundleAdjustments.addAll(convertRulesToDetail(adjustedFact.getAdjustments(), grossTotal));

        // 5. Aggregate Final Amounts
        BigDecimal totalAdjustments = bundleAdjustments.stream()
                .map(PriceComponentDetail::getCalculatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return BundlePriceResponse.builder()
                .productBundleId(request.getProductBundleId())
                .grossTotalAmount(grossTotal)
                .bundleAdjustments(bundleAdjustments)
                .productResults(productResults)
                .netTotalAmount(grossTotal.add(totalAdjustments))
                .build();
    }

    /**
     * Converts the structured BundleAdjustment facts into UI-friendly details.
     */
    private List<PriceComponentDetail> convertRulesToDetail(Map<String, BundlePricingInput.BundleAdjustment> adjustments, BigDecimal grossTotal) {
        if (adjustments == null) return new ArrayList<>();

        return adjustments.entrySet().stream().map(entry -> {
            String code = entry.getKey();
            BundlePricingInput.BundleAdjustment adj = entry.getValue();
            PriceValue.ValueType valueType = PriceValue.ValueType.valueOf(adj.getType());

            // Calculate monetary value based on type
            BigDecimal calculatedAmount = switch (valueType) {
                case DISCOUNT_PERCENTAGE, FEE_PERCENTAGE -> grossTotal.multiply(adj.getValue())
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                default -> adj.getValue();
            };

            return PriceComponentDetail.builder()
                    .componentCode(code)
                    .rawValue(adj.getValue())
                    .calculatedAmount(calculatedAmount)
                    .sourceType("BUNDLE_RULES")
                    .valueType(valueType)
                    .build();
        }).collect(Collectors.toList());
    }
}