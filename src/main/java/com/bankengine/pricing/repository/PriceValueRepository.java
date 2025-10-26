package com.bankengine.pricing.repository;
import com.bankengine.pricing.model.PriceValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PriceValueRepository extends JpaRepository<PriceValue, Long> {
    Optional<PriceValue> findByPricingTierId(Long tierId);
    void deleteByPricingTierId(Long tierId);
}