package com.bankengine.pricing.repository;

import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PricingTierRepository extends JpaRepository<PricingTier, Long> {

    /**
     * Finds all Pricing Tiers associated with a specific Pricing Component.
     */
    List<PricingTier> findAllByPricingComponent(PricingComponent pricingComponent);

    // Used by PricingComponentService to check for dependencies
    boolean existsByPricingComponentId(Long componentId);

    // Used to return a helpful message showing the count of dependencies
    long countByPricingComponentId(Long componentId);
}