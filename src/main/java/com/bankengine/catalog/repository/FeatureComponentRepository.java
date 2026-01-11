package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.common.repository.TenantRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FeatureComponentRepository extends TenantRepository<FeatureComponent, Long> {
    boolean existsByName(String name);
    Optional<FeatureComponent> findByName(String name);
}