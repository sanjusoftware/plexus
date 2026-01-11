package com.bankengine.pricing.repository;

import com.bankengine.common.repository.TenantRepository;
import com.bankengine.pricing.model.ProductPricingLink;

import java.util.List;

public interface ProductPricingLinkRepository extends TenantRepository<ProductPricingLink, Long> {
    List<ProductPricingLink> findByProductId(Long productId);
    long countByPricingComponentId(Long pricingComponentId);
    boolean existsByPricingComponentIdAndProductId(Long pricingComponentId, Long productId);
}