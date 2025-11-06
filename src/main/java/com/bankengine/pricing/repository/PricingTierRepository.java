package com.bankengine.pricing.repository;

import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import jakarta.persistence.QueryHint;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;

public interface PricingTierRepository extends JpaRepository<PricingTier, Long> {

    /**
     * Finds all Pricing Tiers associated with a specific Pricing Component.
     */
    List<PricingTier> findAllByPricingComponent(PricingComponent pricingComponent);

    // Used by PricingComponentService to check for dependencies
    boolean existsByPricingComponentId(Long componentId);

    // Query hints to prevent caching of the result
    @QueryHints({
            @QueryHint(name = "org.hibernate.cacheable", value = "false"),
            @QueryHint(name = "jakarta.persistence.cache.retrieveMode", value = "BYPASS")
    })
    long countByPricingComponentId(Long componentId);

     @Modifying
     @Transactional
     @Query("DELETE FROM PricingTier pt WHERE pt.id = ?1")
     void deleteById(Long tierId);
}