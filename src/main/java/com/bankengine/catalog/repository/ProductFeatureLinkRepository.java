package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.common.repository.TenantRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductFeatureLinkRepository extends TenantRepository<ProductFeatureLink, Long> {
    List<ProductFeatureLink> findByProductId(Long productId);
    boolean existsByFeatureComponentId(Long featureComponentId);
    long countByFeatureComponentId(Long featureComponentId);
}