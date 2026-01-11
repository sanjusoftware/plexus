package com.bankengine.pricing.repository;

import com.bankengine.common.repository.TenantRepository;
import com.bankengine.pricing.model.BundlePricingLink;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BundlePricingLinkRepository extends TenantRepository<BundlePricingLink, Long> {
    List<BundlePricingLink> findByProductBundleId(Long productBundleId);
}