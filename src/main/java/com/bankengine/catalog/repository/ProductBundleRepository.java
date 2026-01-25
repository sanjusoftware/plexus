package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.common.repository.TenantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface ProductBundleRepository extends TenantRepository<ProductBundle, Long> {

    @Query("SELECT b FROM ProductBundle b WHERE b.status = :status " +
           "AND b.activationDate <= :targetDate " +
           "AND (b.expiryDate IS NULL OR b.expiryDate >= :targetDate)")
    Page<ProductBundle> findActiveBundles(
            @Param("status") ProductBundle.BundleStatus status,
            @Param("targetDate") LocalDate targetDate,
            Pageable pageable);
}