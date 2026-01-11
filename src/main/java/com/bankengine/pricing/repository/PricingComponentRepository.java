package com.bankengine.pricing.repository;

import com.bankengine.common.repository.TenantRepository;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingComponent.ComponentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface PricingComponentRepository extends TenantRepository<PricingComponent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PricingComponent> findByName(String name);

    /**
     * Fetches all components, eagerly initializing tiers, values, and conditions
     * for use in Drools rule generation.
     */
    @EntityGraph(value = "component-with-tiers-values-conditions", type = EntityGraph.EntityGraphType.LOAD)
    List<PricingComponent> findAll();

    /**
     * Finds all PricingComponents whose type is in the provided list.
     */
    List<PricingComponent> findByTypeIn(List<ComponentType> types);
}