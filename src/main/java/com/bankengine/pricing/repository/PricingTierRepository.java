package com.bankengine.pricing.repository;

import com.bankengine.common.repository.TenantRepository;
import com.bankengine.pricing.model.PricingTier;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.QueryHints;

public interface PricingTierRepository extends TenantRepository<PricingTier, Long> {

    @QueryHints({
            @QueryHint(name = "org.hibernate.cacheable", value = "false"),
            @QueryHint(name = "jakarta.persistence.cache.retrieveMode", value = "BYPASS")
    })
    long countByPricingComponentId(Long componentId);

//     @Transactional
//     void deleteById(@NonNull Long tierId);
}