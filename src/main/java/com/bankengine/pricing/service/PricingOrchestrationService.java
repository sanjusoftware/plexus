package com.bankengine.pricing.service;

import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.pricing.dto.*;
import com.bankengine.rules.model.BundlePricingInput;
import com.bankengine.rules.service.BundleRulesEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PricingOrchestrationService {

    private final PricingCalculationService productPricingService;
    private final BundleRulesEngineService bundleRulesEngineService;
    private final ProductRepository productRepository;

    public ConsolidatedPriceResponse calculateTotalPricing(ConsolidatedPriceRequest request) {
        List<ProductPriceResultDto> productResults = request.getProductRequests().stream()
                .map(this::calculateGrossPriceForProduct)
                .toList();

        BigDecimal grossTotal = productResults.stream()
                .map(ProductPriceResultDto::getGrossPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String customerSegment = request.getClientAttributes().get("customerSegment");

        BundlePricingInput bundleInput = new BundlePricingInput(
                request.getBankId(),
                customerSegment,
                grossTotal
        );

        BundlePricingInput adjustedInput = bundleRulesEngineService.determineBundleAdjustments(bundleInput);

        return new ConsolidatedPriceResponse(
                request.getBankId(),
                grossTotal,
                adjustedInput.getAdjustments(),
                adjustedInput.getNetTotalAmount(),
                productResults
        );
    }

    private ProductPriceResultDto calculateGrossPriceForProduct(PriceRequest priceRequest) {
        ProductPricingCalculationResult result = productPricingService.getProductPricing(priceRequest);

        String productName = productRepository.findById(priceRequest.getProductId())
                .map(Product::getName)
                .orElseGet(() -> "Unknown Product (" + priceRequest.getProductId() + ")");

        return new ProductPriceResultDto(
                priceRequest.getProductId(),
                productName,
                result.getFinalChargeablePrice(),
                result.getComponentBreakdown()
        );
    }
}