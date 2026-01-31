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
    /**
     * Finds all active bundle pricing links for a specific bundle on a given date.
     * Logic: effectiveDate <= date AND expiryDate >= date
     */
    @Query("SELECT b FROM BundlePricingLink b " +
            "WHERE b.productBundle.id = :bundleId " +
            "AND b.effectiveDate <= :date " +
            "AND b.expiryDate >= :date")
    List<BundlePricingLink> findByBundleIdAndDate(@Param("bundleId") Long bundleId,
                                                  @Param("date") LocalDate date);
}