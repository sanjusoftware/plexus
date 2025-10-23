package com.bankengine.pricing.service;

import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.repository.PricingTierRepository;
import com.bankengine.pricing.repository.PriceValueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PricingComponentService {

    private final PricingComponentRepository componentRepository;
    private final PricingTierRepository tierRepository;
    private final PriceValueRepository valueRepository;

    public PricingComponentService(
            PricingComponentRepository componentRepository,
            PricingTierRepository tierRepository,
            PriceValueRepository valueRepository) {
        this.componentRepository = componentRepository;
        this.tierRepository = tierRepository;
        this.valueRepository = valueRepository;
    }

    /**
     * Creates a new global Pricing Component (e.g., 'Annual Fee').
     */
    @Transactional
    public PricingComponent createComponent(PricingComponent component) {
        // Simple validation can be added here (e.g., uniqueness)
        return componentRepository.save(component);
    }

    /**
     * Retrieves a Pricing Component by ID.
     */
    public Optional<PricingComponent> getComponentById(Long id) {
        return componentRepository.findById(id);
    }

    /**
     * Links a new Tier and its Price Value to an existing Pricing Component.
     * This method handles the complex hierarchy creation.
     */
    @Transactional
    public PriceValue addTierAndValue(Long componentId, PricingTier tier, PriceValue priceAmount) {

        // 1. Validate the component exists
        PricingComponent component = componentRepository.findById(componentId)
                .orElseThrow(() -> new IllegalArgumentException("Pricing Component not found."));

        // 2. Link the Component to the Tier and save the Tier
        tier.setPricingComponent(component);
        PricingTier savedTier = tierRepository.save(tier);

        // 3. Link the Tier to the priceAmount and save the priceAmount
        priceAmount.setPricingTier(savedTier);
        PriceValue savedValue = valueRepository.save(priceAmount);

        return savedValue;
    }

    // Future methods will include: getPriceByCriteria, applyDiscountRules, etc.
}