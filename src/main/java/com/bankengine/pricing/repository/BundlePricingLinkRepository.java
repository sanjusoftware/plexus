package com.bankengine.pricing.repository;

import com.bankengine.common.repository.TenantRepository;
import com.bankengine.pricing.model.BundlePricingLink;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BundlePricingLinkRepository extends TenantRepository<BundlePricingLink, Long> {
    @Query("SELECT DISTINCT b FROM BundlePricingLink b " +
            "JOIN FETCH b.pricingComponent c " +
            "LEFT JOIN FETCH c.pricingTiers t " +
            "WHERE b.productBundle.id = :bundleId " +
            "AND (b.effectiveDate IS NULL OR b.effectiveDate <= :cycleEnd) " +
            "AND (b.expiryDate IS NULL OR b.expiryDate >= :cycleStart)")
    List<BundlePricingLink> findByBundleIdOverlappingCycle(@Param("bundleId") Long bundleId,
                                                           @Param("cycleStart") LocalDate cycleStart,
                                                           @Param("cycleEnd") LocalDate cycleEnd);
}