package com.bankengine.pricing.repository;

import com.bankengine.common.repository.TenantRepository;
import com.bankengine.pricing.model.TierCondition;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TierConditionRepository extends TenantRepository<TierCondition, Long> {
    boolean existsByAttributeName(String attributeName);

    @Transactional
    void deleteByPricingTierId(Long pricingTierId);

    @Transactional
    void deleteByPricingTierIdIn(List<Long> pricingTierIds);
}