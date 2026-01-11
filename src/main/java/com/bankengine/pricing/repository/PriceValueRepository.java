package com.bankengine.pricing.repository;

import com.bankengine.common.repository.TenantRepository;
import com.bankengine.pricing.model.PriceValue;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

public interface PriceValueRepository extends TenantRepository<PriceValue, Long> {
    Optional<PriceValue> findByPricingTierId(Long tierId);

    @Transactional
    void deleteByPricingTierId(Long tierId);

    @Transactional
    void deleteByPricingTierIdIn(List<Long> pricingTierIds);
}