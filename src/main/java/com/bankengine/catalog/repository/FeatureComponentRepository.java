package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.FeatureComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FeatureComponentRepository extends JpaRepository<FeatureComponent, Long> {

    // Allows us to check if a feature name already exists globally
    boolean existsByName(String name);

    Optional<FeatureComponent> findByName(String name);
}