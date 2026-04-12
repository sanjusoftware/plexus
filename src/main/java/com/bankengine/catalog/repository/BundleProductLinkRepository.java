package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.BundleProductLink;
import com.bankengine.common.repository.TenantRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

public interface BundleProductLinkRepository extends TenantRepository<BundleProductLink, Long> {
    List<BundleProductLink> findAllByProductId(Long productId);

    @Query("select l from BundleProductLink l join fetch l.productBundle b join fetch l.product p where l.bankId = :bankId and b.status in :statuses")
    List<BundleProductLink> findAllByBankIdAndBundleStatuses(@Param("bankId") String bankId,
                                                             @Param("statuses") Set<com.bankengine.common.model.VersionableEntity.EntityStatus> statuses);

    @Modifying
    @Transactional
    @Query("UPDATE BundleProductLink l SET l.product = :newProduct WHERE l.product.id = :oldId")
    void updateProductReference(@Param("oldId") Long oldId, @Param("newProduct") com.bankengine.catalog.model.Product newProduct);
}