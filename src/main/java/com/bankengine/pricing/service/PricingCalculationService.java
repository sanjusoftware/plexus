package com.bankengine.pricing.service;

import com.bankengine.pricing.dto.CalculatedPriceDto;
import com.bankengine.pricing.model.ProductPricingLink;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.repository.ProductPricingLinkRepository;
import com.bankengine.pricing.repository.PricingTierRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PricingCalculationService {

    private final ProductPricingLinkRepository linkRepository;
    private final PricingTierRepository tierRepository;

    public PricingCalculationService(
            ProductPricingLinkRepository linkRepository,
            PricingTierRepository tierRepository) {
        this.linkRepository = linkRepository;
        this.tierRepository = tierRepository;
    }

    /**
     * Calculates the price/rate for a product based on customer and transaction data.
     * This simulates the key Plexus functionality.
     * * @param productId ID of the product.
     * @param customerSegment The segment of the customer (e.g., HNW, STANDARD).
     * @param transactionAmount The amount involved (e.g., loan size, annual spend).
     * @return A map of components and their final calculated values.
     */
    public List<CalculatedPriceDto> calculateProductPrice(
            Long productId, String customerSegment, BigDecimal transactionAmount) {

        // 1. Find all relevant pricing components for this product.
        List<ProductPricingLink> links = linkRepository.findByProductId(productId);

        // 2. Iterate through each component and find the matching price.
        return links.stream()
                .map(link -> {
                    PricingComponent component = link.getPricingComponent();

                    // 3. Find Tiers associated with this component
                    // NOTE: This repository call needs to be efficient in a real app.
                    List<PricingTier> tiers = tierRepository.findByPricingComponent(component);

                    // 4. RULE ENGINE SIMULATION: Find the matching Tier/Value
                    PriceValue finalValue = findMatchingPriceValue(tiers, customerSegment, transactionAmount);

                    return new CalculatedPriceDto(
                            component.getName(),
                            finalValue != null ? finalValue.getPriceAmount() : BigDecimal.ZERO,
                            finalValue != null ? finalValue.getValueType().name() : "N/A"
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Internal method to simulate a complex lookup/Rule Engine decision.
     */
    private PriceValue findMatchingPriceValue(
            List<PricingTier> tiers, String customerSegment, BigDecimal transactionAmount) {

        // This is where the core logic of the Rules Engine (RMS) would live.
        // It would execute a Drools or proprietary rule set.

        for (PricingTier tier : tiers) {
            // Check Customer Segment (simple check)
            if (tier.getConditionKey() != null && tier.getConditionValue().equalsIgnoreCase(customerSegment)) {
                // Check Numeric Threshold (e.g., loan size or spend)
                boolean matchesThreshold = tier.getMinThreshold() == null || transactionAmount.compareTo(tier.getMinThreshold()) >= 0;
                // Below min
                if (tier.getMaxThreshold() != null && transactionAmount.compareTo(tier.getMaxThreshold()) > 0) {
                    matchesThreshold = false; // Above max
                }

                if (matchesThreshold && tier.getPriceValue() != null && !tier.getPriceValue().isEmpty()) {
                    // For simplicity, assume one PriceValue per Tier for now.
                    // In reality, this links to PriceValue entity.

                    // We need to fetch the PriceValue from the Tier (requires getter/link in Tier entity)
                    // For now, let's use a placeholder.

                    // FIX: We need a direct link from PricingTier to PriceValue for simplicity in the seeder.
                    // Let's assume the first value in the PriceValueRepository linked by tier ID is the one.
                    // This is messy, so let's simplify the data model retrieval.
                    return tier.getPriceValue().stream().findFirst().orElse(null);
                }
            }
        }
        return null; // No match found
    }
}