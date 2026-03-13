package com.bankengine.pricing.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

import com.bankengine.common.repository.VersionableRepository;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingComponent.ComponentType;

public interface PricingComponentRepository extends VersionableRepository<PricingComponent> {
    @EntityGraph(value = "component-with-tiers-values-conditions", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT DISTINCT pc FROM PricingComponent pc")
    List<PricingComponent> findAllWithDetailsBy();

    @Query("SELECT DISTINCT pc FROM PricingComponent pc WHERE pc.type IN :types")
    List<PricingComponent> findByTypeIn(List<ComponentType> types);
}