package com.bankengine.pricing.repository;
import com.bankengine.pricing.model.PricingTier;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PricingTierRepository extends JpaRepository<PricingTier, Long> {}
