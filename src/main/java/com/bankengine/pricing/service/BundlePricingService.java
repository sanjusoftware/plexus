package com.bankengine.pricing.service;

import com.bankengine.auth.security.BankContextHolder;
import com.bankengine.pricing.dto.BundlePriceRequest;
import com.bankengine.pricing.dto.BundlePriceResponse;
import com.bankengine.pricing.dto.BundlePriceResponse.ProductPricingResult;
import com.bankengine.pricing.dto.PriceRequest;
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
public class BundlePricingService {

    private final PricingCalculationService pricingCalculationService;
    private final BundleRulesEngineService bundleRulesEngineService;

    @Transactional(readOnly = true)
    public BundlePriceResponse calculateTotalBundlePrice(BundlePriceRequest request) {

        if (request.getProducts() == null || request.getProducts().isEmpty()) {
            throw new NotFoundException("The bundle must contain at least one product.");
        }

        List<ProductPricingResult> productResults = new ArrayList<>();
        BigDecimal grossTotal = BigDecimal.ZERO;
        String currentBankId = BankContextHolder.getBankId();

        for (BundlePriceRequest.ProductRequest productReq : request.getProducts()) {
            PriceRequest singlePriceRequest = new PriceRequest();
            singlePriceRequest.setProductId(productReq.getProductId());
            singlePriceRequest.setAmount(productReq.getAmount());
            singlePriceRequest.setCustomerSegment(request.getCustomerSegment());
            singlePriceRequest.setEffectiveDate(request.getEffectiveDate());

            ProductPricingCalculationResult result = pricingCalculationService.getProductPricing(singlePriceRequest);

            BigDecimal productTotal = result.getFinalChargeablePrice();
            grossTotal = grossTotal.add(productTotal);

            productResults.add(ProductPricingResult.builder()
                    .productId(productReq.getProductId())
                    .productTotalAmount(productTotal)
                    .pricingComponents(result.getComponentBreakdown())
                    .build());
        }

        BundlePricingInput bundleInputFact = new BundlePricingInput();
        bundleInputFact.setBankId(currentBankId);
        bundleInputFact.setCustomerSegment(request.getCustomerSegment());
        bundleInputFact.setGrossTotalAmount(grossTotal);

        BundlePricingInput adjustedFact = bundleRulesEngineService.determineBundleAdjustments(bundleInputFact);

        List<PriceComponentDetail> bundleAdjustments = convertAdjustmentsToDetail(adjustedFact.getAdjustments());

        BigDecimal adjustmentTotal = bundleAdjustments.stream()
                .map(PriceComponentDetail::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return BundlePriceResponse.builder()
                .productBundleId(request.getProductBundleId())
                .grossTotalAmount(grossTotal)
                .bundleAdjustments(bundleAdjustments)
                .productResults(productResults)
                .netTotalAmount(grossTotal.add(adjustmentTotal))
                .build();
    }

    private List<PriceComponentDetail> convertAdjustmentsToDetail(Map<String, BigDecimal> adjustments) {
        return adjustments.entrySet().stream().map(entry -> PriceComponentDetail.builder()
            .componentCode(entry.getKey())
            .amount(entry.getValue())
            .context("BUNDLE_ADJUSTMENT")
            .sourceType("BUNDLE_RULES")
            .valueType(PriceValue.ValueType.DISCOUNT_ABSOLUTE)
            .build()
        ).collect(Collectors.toList());
    }
}