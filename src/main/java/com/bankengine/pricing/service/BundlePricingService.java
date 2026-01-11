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
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.rules.model.BundlePricingInput;
import com.bankengine.rules.service.BundleRulesEngineService;
import com.bankengine.web.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

        // 3. Process Fixed Adjustments from Database (BundlePricingLink)
        if (bundle.getBundlePricingLinks() != null) {
            bundle.getBundlePricingLinks().stream()
                .filter(link -> !link.isUseRulesEngine() && link.getFixedValue() != null)
                .forEach(link -> bundleAdjustments.add(PriceComponentDetail.builder()
                        .componentCode(link.getPricingComponent().getName())
                        .amount(link.getFixedValue())
                        .valueType(PriceValue.ValueType.ABSOLUTE)
                        .sourceType("FIXED_VALUE")
                        .build()));
        }

        // 4. Process Dynamic Adjustments from Rules Engine (Drools)
        BundlePricingInput bundleInputFact = new BundlePricingInput();
        bundleInputFact.setBankId(getCurrentBankId());
        bundleInputFact.setCustomerSegment(request.getCustomerSegment());
        bundleInputFact.setGrossTotalAmount(grossTotal);

        BundlePricingInput adjustedFact = bundleRulesEngineService.determineBundleAdjustments(bundleInputFact);
        bundleAdjustments.addAll(convertRulesToDetail(adjustedFact.getAdjustments()));

        // 5. Aggregate Final Amounts
        BigDecimal totalAdjustments = bundleAdjustments.stream()
                .map(PriceComponentDetail::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return BundlePriceResponse.builder()
                .productBundleId(request.getProductBundleId())
                .grossTotalAmount(grossTotal)
                .bundleAdjustments(bundleAdjustments)
                .productResults(productResults)
                .netTotalAmount(grossTotal.add(totalAdjustments))
                .build();
    }

    private List<PriceComponentDetail> convertRulesToDetail(Map<String, BigDecimal> adjustments) {
        if (adjustments == null) return new ArrayList<>();
        return adjustments.entrySet().stream().map(entry -> PriceComponentDetail.builder()
            .componentCode(entry.getKey())
            .amount(entry.getValue())
            .sourceType("BUNDLE_RULES")
            .valueType(PriceValue.ValueType.DISCOUNT_ABSOLUTE)
            .build()
        ).collect(Collectors.toList());
    }
}