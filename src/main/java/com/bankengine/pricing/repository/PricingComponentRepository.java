package com.bankengine.pricing.repository;

import com.bankengine.common.repository.VersionableRepository;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingComponent.ComponentType;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface PricingComponentRepository extends VersionableRepository<PricingComponent> {
    Optional<PricingComponent> findByName(String name);
    @EntityGraph(value = "component-with-tiers-values-conditions", type = EntityGraph.EntityGraphType.LOAD)
    List<PricingComponent> findAllWithDetailsBy();
    List<PricingComponent> findByTypeIn(List<ComponentType> types);
}