package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.BundleProductLink;
import com.bankengine.common.repository.TenantRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface BundleProductLinkRepository extends TenantRepository<BundleProductLink, Long> {
    List<BundleProductLink> findAllByProductId(Long productId);

    @Modifying
    @Transactional
    @Query("UPDATE BundleProductLink l SET l.product = :newProduct WHERE l.product.id = :oldId")
    void updateProductReference(@Param("oldId") Long oldId, @Param("newProduct") com.bankengine.catalog.model.Product newProduct);
}