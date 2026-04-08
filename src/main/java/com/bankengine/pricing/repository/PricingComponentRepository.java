package com.bankengine.pricing.repository;

import com.bankengine.common.repository.VersionableRepository;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingComponent.ComponentType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PricingComponentRepository extends VersionableRepository<PricingComponent> {
    @EntityGraph(value = "component-with-tiers-values-conditions", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT DISTINCT pc FROM PricingComponent pc")
    List<PricingComponent> findAllWithDetailsBy();

    @EntityGraph(value = "component-with-tiers-values-conditions", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT DISTINCT pc FROM PricingComponent pc WHERE pc.type IN :types")
    List<PricingComponent> findByTypeIn(List<ComponentType> types);
}