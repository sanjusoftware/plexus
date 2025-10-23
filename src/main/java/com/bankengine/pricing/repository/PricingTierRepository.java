package com.bankengine.pricing.repository;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PricingTierRepository extends JpaRepository<PricingTier, Long> {
    // CRITICAL ADDITION: Custom finder method required by the Calculation Service
    List<PricingTier> findByPricingComponent(PricingComponent pricingComponent);
}