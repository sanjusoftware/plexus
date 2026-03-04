package com.bankengine.pricing.service;

import com.bankengine.catalog.repository.ProductBundleRepository;
import com.bankengine.common.service.BaseService;
import com.bankengine.pricing.dto.BundlePriceRequest;
import com.bankengine.pricing.dto.BundlePriceResponse;
import com.bankengine.pricing.dto.BundlePriceResponse.ProductPricingResult;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.dto.ProductPricingCalculationResult.PriceComponentDetail;
import com.bankengine.pricing.dto.ProductPricingRequest;
import com.bankengine.pricing.model.BundlePricingLink;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.repository.BundlePricingLinkRepository;
import com.bankengine.rules.model.BundlePricingInput;
import com.bankengine.rules.service.BundleRulesEngineService;
import com.bankengine.web.exception.NotFoundException;
import com.bankengine.web.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BundlePricingService extends BaseService {

    private final ProductPricingService productPricingService;
    private final BundleRulesEngineService bundleRulesEngineService;
    private final ProductBundleRepository productBundleRepository;
    private final BundlePricingLinkRepository bundlePricingLinkRepository;
    private final PriceAggregator priceAggregator;

    /**
     * Calculates the total price for a bundle.
     * High-level orchestration of:
     * 1. Product Discovery
     * 2. Component Assembly (DB + Rules)
     * 3. Mathematical Aggregation
     */
    @Transactional(readOnly = true)
    public BundlePriceResponse calculateTotalBundlePrice(BundlePriceRequest bundlePriceRequest) {
        // 0. Fail Fast: Validate input presence
        validateRequest(bundlePriceRequest);

        // 1. Calculate Individual Product Prices (The Base Fee Pool)
        List<ProductPricingResult> productPricingResults = calculateIndividualProductFee(bundlePriceRequest);
        BigDecimal existingFeePool = aggregatedProductsFee(productPricingResults);

        // 2. Fetch Bundle and Active Temporal Links
        verifyBundleExists(bundlePriceRequest.getProductBundleId());
        List<BundlePricingLink> activeLinks = bundlePricingLinkRepository
                .findByBundleIdAndDate(bundlePriceRequest.getProductBundleId(), bundlePriceRequest.getEffectiveDate());

        // 3. Assemble Components (Fixed from DB + Dynamic from Rules)
        List<PriceComponentDetail> bundleAdjustments = assembleBundleComponents(bundlePriceRequest, activeLinks, existingFeePool);

        // 4. Delegate to Aggregator (The Pure Calculation Engine)
        // This calculates pro-rata and populates 'calculatedAmount' for each adjustment
        BigDecimal netBundleImpact = priceAggregator.calculateBundleImpact(
                bundleAdjustments,
                BigDecimal.ZERO,
                existingFeePool,
                bundlePriceRequest.getEnrollmentDate(),
                bundlePriceRequest.getEffectiveDate());

        // 5. Build Final Response
        return buildResponse(bundlePriceRequest, productPricingResults, bundleAdjustments,
                existingFeePool, netBundleImpact);
    }

    // -----------------------------------------------------------------------------------
    // PRIVATE HELPER METHODS (Internal Logic Steps)
    // -----------------------------------------------------------------------------------

    private void validateRequest(BundlePriceRequest request) {
        if (request.getProducts() == null || request.getProducts().isEmpty()) {
            throw new ValidationException("Bundle has no products.");
        }
    }

    private List<ProductPricingResult> calculateIndividualProductFee(BundlePriceRequest request) {
        List<ProductPricingResult> results = new ArrayList<>();

        for (BundlePriceRequest.ProductRequest productReq : request.getProducts()) {
            ProductPricingRequest singlePricingRequest = ProductPricingRequest.builder()
                    .productId(productReq.getProductId())
                    .transactionAmount(productReq.getTransactionAmount()) // Base amount for product-specific rules
                    .customerSegment(request.getCustomerSegment())
                    .effectiveDate(request.getEffectiveDate())
                    .build();

            ProductPricingCalculationResult calcResult = productPricingService.getProductPricing(singlePricingRequest);

            if (calcResult == null) {
                log.error("Pricing service returned null for product ID: {}", productReq.getProductId());
                throw new IllegalStateException("Could not calculate price for product: " + productReq.getProductId());
            }

            BigDecimal productPrice = calcResult.getFinalChargeablePrice() != null ?
                    calcResult.getFinalChargeablePrice() : BigDecimal.ZERO;

            results.add(ProductPricingResult.builder()
                    .productId(productReq.getProductId())
                    .productTotalFee(productPrice)
                    .pricingComponents(calcResult.getComponentBreakdown())
                    .build());
        }
        return results;
    }

    private BigDecimal aggregatedProductsFee(List<ProductPricingResult> results) {
        return results.stream()
                .map(ProductPricingResult::getProductTotalFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void verifyBundleExists(Long bundleId) {
        productBundleRepository.findById(bundleId)
                .orElseThrow(() -> new NotFoundException("Product Bundle not found with ID: " + bundleId));
    }

    /**
     * Orchestrates the gathering of all bundle-level adjustment definitions.
     */
    private List<PriceComponentDetail> assembleBundleComponents(BundlePriceRequest request,
                                                                List<BundlePricingLink> activeLinks,
                                                                BigDecimal existingFeePool) {
        List<PriceComponentDetail> adjustments = new ArrayList<>();

        // Add Fixed Adjustments (DB)
        activeLinks.stream()
                .filter(link -> !link.isUseRulesEngine() && link.getFixedValue() != null)
                .forEach(link -> adjustments.add(mapFixedLinkToDetail(link)));

        // Add Dynamic Adjustments (Drools)
        BundlePricingInput rulesOutput = fireRulesEngine(request, activeLinks, existingFeePool);
        adjustments.addAll(convertRulesToDetail(rulesOutput.getAdjustments()));

        return adjustments;
    }

    private PriceComponentDetail mapFixedLinkToDetail(BundlePricingLink link) {
        PriceValue.ValueType type = link.getFixedValueType() != null ?
                link.getFixedValueType() : PriceValue.ValueType.FEE_ABSOLUTE;

        // Determine if any associated tier has the full breach flag set
        boolean isFullBreach = link.getPricingComponent().getPricingTiers().stream()
                .anyMatch(PricingTier::isApplyChargeOnFullBreach);

        return PriceComponentDetail.builder()
                .componentCode(link.getPricingComponent().getName())
                .rawValue(link.getFixedValue())
                .valueType(type)
                .sourceType("FIXED_VALUE")
                .applyChargeOnFullBreach(isFullBreach)
                .proRataApplicable(true)
                .build();
    }

    private BundlePricingInput fireRulesEngine(BundlePriceRequest request, List<BundlePricingLink> activeLinks, BigDecimal productBaseFee) {
        BundlePricingInput inputFact = new BundlePricingInput();
        inputFact.setGrossTotalAmount(productBaseFee);
        inputFact.setBankId(getCurrentBankId());
        inputFact.setCustomerSegment(request.getCustomerSegment());
        inputFact.setReferenceDate(request.getEffectiveDate());

        // 1. Filter links that require the Rules Engine
        List<BundlePricingLink> ruleLinks = activeLinks.stream()
                .filter(BundlePricingLink::isUseRulesEngine)
                .toList();

        // 2. Identify Target Components
        Set<Long> targetComponentIds = ruleLinks.stream()
                .map(link -> link.getPricingComponent().getId())
                .collect(Collectors.toSet());
        inputFact.setTargetPricingComponentIds(targetComponentIds);

        // 3. NEW: Collect Tier IDs for the active components to satisfy DRL filtering
        // Since Tiers now depend on Component activation, we harvest all tiers from active components
        Set<Long> activeTierIds = ruleLinks.stream()
                .flatMap(link -> link.getPricingComponent().getPricingTiers().stream())
                .map(PricingTier::getId)
                .collect(Collectors.toSet());
        inputFact.setActivePricingTierIds(activeTierIds);

        // 4. Populate Context Attributes for custom DRL expressions
        inputFact.getCustomAttributes().put("customerSegment", request.getCustomerSegment());
        inputFact.getCustomAttributes().put("effectiveDate", request.getEffectiveDate());

        if (request.getCustomAttributes() != null) {
            inputFact.getCustomAttributes().putAll(request.getCustomAttributes());
        }

        return bundleRulesEngineService.determineBundleAdjustments(inputFact);
    }

    private List<PriceComponentDetail> convertRulesToDetail(Map<String, BundlePricingInput.BundleAdjustment> adjustments) {
        if (adjustments == null) return new ArrayList<>();

        return adjustments.entrySet().stream().map(entry -> {
            PriceValue.ValueType type = PriceValue.ValueType.valueOf(entry.getValue().getType());
            return PriceComponentDetail.builder()
                    .componentCode(entry.getKey())
                    .rawValue(entry.getValue().getValue())
                    .sourceType("BUNDLE_RULES")
                    .valueType(type)
                    .targetComponentCode(entry.getValue().getTargetComponentCode())
                    .applyChargeOnFullBreach(entry.getValue().isApplyChargeOnFullBreach())
                    .build();
        }).toList();
    }

    private BundlePriceResponse buildResponse(BundlePriceRequest request,
                                              List<ProductPricingResult> productResults,
                                              List<PriceComponentDetail> adjustments,
                                              BigDecimal existingFeePool,
                                              BigDecimal netBundleImpact) {

        // Sum only the positive additions (Fees) to calculate Gross
        BigDecimal bundleFeesOnly = adjustments.stream()
                .filter(adj -> isFee(adj.getValueType()))
                .map(PriceComponentDetail::getCalculatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return BundlePriceResponse.builder()
                .productBundleId(request.getProductBundleId())
                .grossTotalAmount(existingFeePool.add(bundleFeesOnly))
                .netTotalAmount(existingFeePool.add(netBundleImpact))
                .bundleAdjustments(adjustments)
                .productResults(productResults)
                .build();
    }

    private boolean isDiscount(PriceValue.ValueType type) {
        return type == PriceValue.ValueType.DISCOUNT_ABSOLUTE || type == PriceValue.ValueType.DISCOUNT_PERCENTAGE;
    }

    private boolean isFee(PriceValue.ValueType type) {
        return type == PriceValue.ValueType.FEE_ABSOLUTE || type == PriceValue.ValueType.FEE_PERCENTAGE;
    }
}