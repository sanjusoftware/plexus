package com.bankengine.pricing.service;

import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.pricing.dto.*;
import com.bankengine.rules.model.BundlePricingInput;
import com.bankengine.rules.service.BundleRulesEngineService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

// NOTE: You will need to define the DTOs (ConsolidatedPriceRequest, etc.) in your project
// based on the structure below.

/**
 * Orchestrates the two-stage pricing process:
 * 1. Calculates individual product component prices (Gross Total).
 * 2. Applies bundle rules (Discounts/Waivers) to the Gross Total (Adjustments).
 * 3. Returns the final Net Total.
 */
@Service
public class PricingOrchestrationService {

    private final PricingCalculationService productPricingService;
    private final BundleRulesEngineService bundleRulesEngineService;
    private final ProductRepository productRepository;

    public PricingOrchestrationService(
            PricingCalculationService productPricingService,
            BundleRulesEngineService bundleRulesEngineService, ProductRepository productRepository) {
        this.productPricingService = productPricingService;
        this.bundleRulesEngineService = bundleRulesEngineService;
        this.productRepository = productRepository;
    }

    /**
     * Executes the full two-stage pricing process for a list of products.
     * @param request The request containing multiple product IDs, bank ID, and client attributes.
     * @return The final consolidated price response.
     */
    public ConsolidatedPriceResponse calculateTotalPricing(ConsolidatedPriceRequest request) {

        // --- STAGE 1: Component Pricing (Calculate Gross Total) ---

        // This requires updating your existing services/DTOs to work with ProductPriceResultDto
        // which includes the aggregate price per product. For now, we simulate the structure.
        List<ProductPriceResultDto> productResults = request.getProductRequests().stream()
                .map(this::calculateGrossPriceForProduct) // Adapter method for existing service
                .toList();

        BigDecimal grossTotal = productResults.stream()
                .map(ProductPriceResultDto::getGrossPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // --- STAGE 2: Bundle Adjustment (Apply Waivers/Discounts) ---

        // The DRL will use the bankId and customerSegment from this fact
        String customerSegment = request.getClientAttributes().get("customerSegment");

        BundlePricingInput bundleInput = new BundlePricingInput(
                request.getBankId(),
                customerSegment,
                grossTotal
        );

        BundlePricingInput adjustedInput = bundleRulesEngineService.determineBundleAdjustments(bundleInput);

        // --- STAGE 3: Final Consolidation ---

        return new ConsolidatedPriceResponse(
                request.getBankId(),
                grossTotal,
                adjustedInput.getAdjustments(),
                adjustedInput.getNetTotalAmount(),
                productResults
        );
    }

    /**
     * Helper method to use the existing PricingCalculationService and aggregate its result.
     * NOTE: This is a placeholder. You may need to modify PricingCalculationService
     * to return a simpler DTO that includes the total price for the product.
     */
    private ProductPriceResultDto calculateGrossPriceForProduct(PriceRequest priceRequest) {
        // Use the existing service to get all component prices for one product
        List<PriceValueResponseDto> components = productPricingService.getProductPricing(priceRequest);

        // Aggregate component prices (assuming ABSOLUTE types are summed)
        BigDecimal grossPrice = components.stream()
                .filter(c -> "ABSOLUTE".equals(c.getValueType().name())) // Only sum absolute fees
                .map(PriceValueResponseDto::getPriceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String productName = productRepository.findById(priceRequest.getProductId())
                .map(Product::getName) // Assuming Product has a getName() method
                .orElseGet(() -> "Unknown Product (" + priceRequest.getProductId() + ")");

        return new ProductPriceResultDto(
                priceRequest.getProductId(),
                productName,
                grossPrice,
                components
        );
    }
}