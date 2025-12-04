package com.bankengine.pricing.service;

import com.bankengine.auth.security.BankContextHolder;
import com.bankengine.pricing.dto.BundlePriceRequest;
import com.bankengine.pricing.dto.BundlePriceResponse;
import com.bankengine.pricing.dto.BundlePriceResponse.ProductPricingResult;
import com.bankengine.pricing.dto.PriceRequest;
import com.bankengine.pricing.dto.PriceValueResponseDto;
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

    /**
     * Calculates the aggregated price for an entire product bundle, including individual product pricing
     * and bundle-level adjustments using the dedicated rules engine.
     * @param request The bundle pricing request DTO.
     * @return A comprehensive response DTO with aggregated and itemized prices.
     */
    @Transactional(readOnly = true)
    public BundlePriceResponse calculateTotalBundlePrice(BundlePriceRequest request) {

        if (request.getProducts() == null || request.getProducts().isEmpty()) {
            throw new NotFoundException("The bundle must contain at least one product for pricing.");
        }

        List<ProductPricingResult> productResults = new ArrayList<>();
        BigDecimal grossTotal = BigDecimal.ZERO;
        String currentBankId = BankContextHolder.getBankId(); // Fetch bank ID once

        // 1. Calculate pricing for each individual product in the bundle
        for (BundlePriceRequest.ProductRequest productReq : request.getProducts()) {

            // a. Prepare the single-product request DTO
            PriceRequest singlePriceRequest = new PriceRequest();
            singlePriceRequest.setProductId(productReq.getProductId());
            singlePriceRequest.setAmount(productReq.getAmount());
            singlePriceRequest.setCustomerSegment(request.getCustomerSegment());
            singlePriceRequest.setEffectiveDate(request.getEffectiveDate());

            // b. Call the existing PricingCalculationService
            List<PriceValueResponseDto> components = pricingCalculationService.getProductPricing(singlePriceRequest);

            // c. Aggregate and store the product result
            BigDecimal productTotal = components.stream()
                    // Sum all prices for the product's total amount (assuming PriceValueResponseDto::getPriceAmount is non-null)
                    .map(PriceValueResponseDto::getPriceAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            grossTotal = grossTotal.add(productTotal);

            productResults.add(ProductPricingResult.builder()
                    .productId(productReq.getProductId())
                    .productTotalAmount(productTotal)
                    .pricingComponents(components)
                    .build());
        }

        // 2. Apply bundle-level adjustments using the Rules Engine

        // a. Prepare the Bundle Rules Input Fact
        BundlePricingInput bundleInputFact = new BundlePricingInput();
        bundleInputFact.setBankId(currentBankId); // Pass the multi-tenant ID to the rules
        bundleInputFact.setCustomerSegment(request.getCustomerSegment());
        bundleInputFact.setGrossTotalAmount(grossTotal);

        // b. Run the dedicated bundle rules engine
        BundlePricingInput adjustedFact = bundleRulesEngineService.determineBundleAdjustments(bundleInputFact);

        // c. Convert the Drools output (Map<String, BigDecimal>) to the DTO response list (List<PriceValueResponseDto>)
        List<PriceValueResponseDto> bundleAdjustments = convertAdjustmentsToDto(adjustedFact.getAdjustments());

        // Sum the (negative) adjustments
        BigDecimal adjustmentTotal = bundleAdjustments.stream()
                .map(PriceValueResponseDto::getPriceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Net Total = Gross Total + Adjustments
        BigDecimal netTotal = grossTotal.add(adjustmentTotal);

        // 3. Build and return the final response
        return BundlePriceResponse.builder()
                .productBundleId(request.getProductBundleId())
                .grossTotalAmount(grossTotal)
                .bundleAdjustments(bundleAdjustments)
                .productResults(productResults)
                .netTotalAmount(netTotal)
                .build();
    }

    /**
     * Helper to map the raw Drools output (Map<String, BigDecimal>) into the standardized DTO.
     * **FIX 2:** Updated method signature to correctly accept the Map output from BundlePricingInput.
     */
    private List<PriceValueResponseDto> convertAdjustmentsToDto(Map<String, BigDecimal> adjustments) {
        return adjustments.entrySet().stream().map(entry -> PriceValueResponseDto.builder()
            .pricingComponentCode(entry.getKey())
            .priceAmount(entry.getValue())
            .context("BUNDLE_ADJUSTMENT")
            .sourceType("BUNDLE_RULES")
            .build()
        ).collect(Collectors.toList());
    }
}