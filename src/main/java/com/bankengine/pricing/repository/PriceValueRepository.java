package com.bankengine.pricing.repository;
import com.bankengine.pricing.model.PriceValue;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PriceValueRepository extends JpaRepository<PriceValue, Long> {
    Optional<PriceValue> findByPricingTierId(Long tierId);

    @Modifying
    @Transactional
    @Query("DELETE FROM PriceValue pv WHERE pv.pricingTier.id = ?1")
    void deleteByPricingTierId(Long tierId);

    void deleteByPricingTierIdIn(List<Long> pricingTierIds);
}