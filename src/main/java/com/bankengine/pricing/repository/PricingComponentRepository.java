package com.bankengine.pricing.repository;

import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingComponent.ComponentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PricingComponentRepository extends JpaRepository<PricingComponent, Long> {
    // Custom finder method required by the TestDataSeeder
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PricingComponent> findByName(String name);

    @Query("SELECT t.id FROM PricingTier t WHERE t.pricingComponent.id = :componentId ORDER BY t.id ASC LIMIT 1")
    Optional<Long> findFirstTierIdByComponentId(@Param("componentId") Long componentId);

    /**
     * Fetches all components, eagerly initializing tiers, values, and conditions
     * for use in Drools rule generation.
     */
    @EntityGraph(value = "component-with-tiers-values-conditions", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT c FROM PricingComponent c")
    List<PricingComponent> findAllEagerlyForRules();

    /**
     * Finds all PricingComponents whose type is in the provided list.
     */
    List<PricingComponent> findByTypeIn(List<ComponentType> types);
}