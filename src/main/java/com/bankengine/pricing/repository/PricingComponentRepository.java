package com.bankengine.pricing.repository;
import com.bankengine.pricing.model.PricingComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PricingComponentRepository extends JpaRepository<PricingComponent, Long> {
    // CRITICAL ADDITION: Custom finder method required by the TestDataSeeder
    Optional<PricingComponent> findByName(String name);
}