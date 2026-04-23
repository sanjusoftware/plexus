package com.bankengine.pricing.service;

import com.bankengine.catalog.repository.ProductBundleRepository;
import com.bankengine.common.service.BaseService;
import com.bankengine.pricing.dto.BundlePriceRequest;
import com.bankengine.pricing.dto.BundlePriceResponse;
import com.bankengine.pricing.dto.BundlePriceResponse.ProductPricingResult;
import com.bankengine.pricing.dto.ProductPriceRequest;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.dto.ProductPricingCalculationResult.PriceComponentDetail;
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
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BundlePricingService extends BaseService {

    private record BundleLinkContext(LocalDate effectiveDate, LocalDate expiryDate, boolean proRataApplicable) {}

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
        LocalDate effectiveDate = resolveEffectiveDate(bundlePriceRequest);

        // 1. Calculate Individual Product Prices (The Base Fee Pool)
        List<ProductPricingResult> productPricingResults = calculateIndividualProductFee(bundlePriceRequest);
        BigDecimal existingFeePool = aggregatedProductsFee(productPricingResults);

        // 2. Fetch Bundle and Active Temporal Links
        verifyBundleExists(bundlePriceRequest.getProductBundleId());
        LocalDate cycleStart = effectiveDate.withDayOfMonth(1);
        LocalDate cycleEnd = effectiveDate.withDayOfMonth(effectiveDate.lengthOfMonth());
        List<BundlePricingLink> activeLinks = bundlePricingLinkRepository
                .findByBundleIdOverlappingCycle(bundlePriceRequest.getProductBundleId(), cycleStart, cycleEnd);

        if (activeLinks.isEmpty()) {
            throw new NotFoundException("No active pricing configuration found for bundle: "
                    + bundlePriceRequest.getProductBundleId() + " on date: " + effectiveDate);
        }

        // 3. Assemble Components (Fixed from DB + Dynamic from Rules)
        List<PriceComponentDetail> bundleAdjustments = assembleBundleComponents(bundlePriceRequest, activeLinks, existingFeePool);

        // 4. Delegate to Aggregator (The Pure Calculation Engine)
        // This calculates pro-rata and populates 'calculatedAmount' for each adjustment
        BigDecimal netBundleImpact = priceAggregator.calculateBundleImpact(
                bundleAdjustments,
                BigDecimal.ZERO,
                existingFeePool,
                bundlePriceRequest.getEnrollmentDate(),
                effectiveDate);

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
        if (request.getCustomAttributes() != null) {
            List<String> legacyKeys = request.getCustomAttributes().keySet().stream()
                    .filter(PricingAttributeKeys.LEGACY_ALIASES::contains)
                    .sorted()
                    .toList();
            if (!legacyKeys.isEmpty()) {
                throw new ValidationException("Legacy pricing attribute keys are not supported: "
                        + String.join(", ", legacyKeys)
                        + ". Use canonical keys: "
                        + String.join(", ", PricingAttributeKeys.SYSTEM_KEYS));
            }
        }
    }

    private List<ProductPricingResult> calculateIndividualProductFee(BundlePriceRequest request) {
        List<ProductPricingResult> results = new ArrayList<>();
        LocalDate effectiveDate = resolveEffectiveDate(request);

        for (BundlePriceRequest.BundleProductItem productReq : request.getProducts()) {
            Map<String, Object> productAttributes = new HashMap<>();
            if (request.getCustomAttributes() != null) {
                productAttributes.putAll(request.getCustomAttributes());
            }
            productAttributes.put(PricingAttributeKeys.PRODUCT_ID, productReq.getProductId());
            productAttributes.put(PricingAttributeKeys.TRANSACTION_AMOUNT,
                    productReq.getTransactionAmount() != null ? productReq.getTransactionAmount() : BigDecimal.ZERO);
            productAttributes.put(PricingAttributeKeys.EFFECTIVE_DATE, effectiveDate);

            ProductPriceRequest singlePricingRequest = ProductPriceRequest.builder()
                    .productId(productReq.getProductId())
                    .enrollmentDate(request.getEnrollmentDate())
                    .customAttributes(productAttributes)
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
        adjustments.addAll(convertRulesToDetail(rulesOutput.getAdjustments(), activeLinks));

        return adjustments;
    }

    private PriceComponentDetail mapFixedLinkToDetail(BundlePricingLink link) {
        PriceValue.ValueType type = link.getFixedValueType() != null ?
                link.getFixedValueType() : PriceValue.ValueType.FEE_ABSOLUTE;

        // Determine if any associated tier has the full breach flag set
        boolean isFullBreach = link.getPricingComponent().getPricingTiers().stream()
                .anyMatch(PricingTier::isApplyChargeOnFullBreach);

        return PriceComponentDetail.builder()
                .componentCode(link.getPricingComponent().getCode())
                .rawValue(link.getFixedValue())
                .valueType(type)
                .sourceType("FIXED_VALUE")
                .applyChargeOnFullBreach(isFullBreach)
                .proRataApplicable(link.getPricingComponent().isProRataApplicable())
                .effectiveDate(link.getEffectiveDate())
                .expiryDate(link.getExpiryDate())
                .build();
    }

    private BundlePricingInput fireRulesEngine(BundlePriceRequest request, List<BundlePricingLink> activeLinks, BigDecimal productBaseFee) {
        Map<String, Object> normalizedAttributes = buildNormalizedCustomAttributes(request, productBaseFee);

        BundlePricingInput inputFact = new BundlePricingInput();
        inputFact.setBankId(getCurrentBankId());

        // 1. Filter links that require the Rules Engine
        List<BundlePricingLink> ruleLinks = activeLinks.stream()
                .filter(BundlePricingLink::isUseRulesEngine)
                .toList();

        // 2. Identify Target Component Codes and Versions
        Set<String> targetComponentCodes = ruleLinks.stream()
                .map(link -> link.getPricingComponent().getCode() + ":" + link.getPricingComponent().getVersion())
                .collect(Collectors.toSet());
        inputFact.setTargetPricingComponentCodes(targetComponentCodes);

        // 3. Harvest Tier Codes directly from the active component links.
        Set<String> activeTierCodes = ruleLinks.stream()
                .flatMap(link -> link.getPricingComponent().getPricingTiers().stream())
                .map(PricingTier::getCode)
                .collect(Collectors.toSet());
        inputFact.setActivePricingTierCodes(activeTierCodes);

        // 4. Populate Context Attributes for custom DRL expressions

        inputFact.getCustomAttributes().putAll(normalizedAttributes);

        return bundleRulesEngineService.determineBundleAdjustments(inputFact);
    }

    private Map<String, Object> buildNormalizedCustomAttributes(BundlePriceRequest request, BigDecimal grossTotalAmount) {
        Map<String, Object> attributes = new HashMap<>();
        if (request.getCustomAttributes() != null) {
            attributes.putAll(request.getCustomAttributes());
        }

        attributes.putIfAbsent(PricingAttributeKeys.PRODUCT_BUNDLE_ID, request.getProductBundleId());
        attributes.putIfAbsent(PricingAttributeKeys.EFFECTIVE_DATE, LocalDate.now());
        attributes.putIfAbsent(PricingAttributeKeys.GROSS_TOTAL_AMOUNT, grossTotalAmount);
        attributes.putIfAbsent(PricingAttributeKeys.BANK_ID, getCurrentBankId());
        return attributes;
    }

    private LocalDate resolveEffectiveDate(BundlePriceRequest request) {
        Object fromCustom = request.getCustomAttributes() != null
                ? request.getCustomAttributes().get(PricingAttributeKeys.EFFECTIVE_DATE)
                : null;
        return extractLocalDate(fromCustom, LocalDate.now());
    }

    private LocalDate extractLocalDate(Object value, LocalDate defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof LocalDate date) return date;
        try {
            return LocalDate.parse(value.toString());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private List<PriceComponentDetail> convertRulesToDetail(Map<String, BundlePricingInput.BundleAdjustment> adjustments,
                                                            List<BundlePricingLink> activeLinks) {
        if (adjustments == null) return new ArrayList<>();

        Map<String, BundleLinkContext> linkContextByComponentCode = activeLinks.stream()
                .collect(Collectors.toMap(
                        link -> link.getPricingComponent().getCode(),
                        link -> new BundleLinkContext(
                                link.getEffectiveDate(),
                                link.getExpiryDate(),
                                link.getPricingComponent().isProRataApplicable()
                        ),
                        (existing, replacement) -> existing,
                        HashMap::new
                ));

        return adjustments.entrySet().stream().map(entry -> {
            PriceValue.ValueType type = PriceValue.ValueType.valueOf(entry.getValue().getType());
            BundleLinkContext linkContext = linkContextByComponentCode.get(entry.getKey());
            return PriceComponentDetail.builder()
                    .componentCode(entry.getKey())
                    .rawValue(entry.getValue().getValue())
                    .sourceType("BUNDLE_RULES")
                    .valueType(type)
                    .targetComponentCode(entry.getValue().getTargetComponentCode())
                    .applyChargeOnFullBreach(entry.getValue().isApplyChargeOnFullBreach())
                    .proRataApplicable(linkContext != null && linkContext.proRataApplicable())
                    .effectiveDate(linkContext != null ? linkContext.effectiveDate() : null)
                    .expiryDate(linkContext != null ? linkContext.expiryDate() : null)
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

    private boolean isFee(PriceValue.ValueType type) {
        return type == PriceValue.ValueType.FEE_ABSOLUTE || type == PriceValue.ValueType.FEE_PERCENTAGE;
    }
}