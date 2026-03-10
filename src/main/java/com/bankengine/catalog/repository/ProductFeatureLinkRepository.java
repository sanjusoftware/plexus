package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.common.repository.TenantRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ProductFeatureLinkRepository extends TenantRepository<ProductFeatureLink, Long> {
    List<ProductFeatureLink> findByProductId(Long productId);
    boolean existsByFeatureComponentId(Long featureComponentId);
    long countByFeatureComponentId(Long featureComponentId);

    @Modifying
    @Transactional
    @Query("UPDATE ProductFeatureLink l SET l.featureComponent = :newComponent WHERE l.featureComponent.id = :oldId")
    void updateFeatureComponentReference(@Param("oldId") Long oldId, @Param("newComponent") com.bankengine.catalog.model.FeatureComponent newComponent);
}