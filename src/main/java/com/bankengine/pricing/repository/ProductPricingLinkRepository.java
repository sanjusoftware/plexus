package com.bankengine.pricing.repository;

import com.bankengine.common.repository.TenantRepository;
import com.bankengine.pricing.model.ProductPricingLink;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface ProductPricingLinkRepository extends TenantRepository<ProductPricingLink, Long> {

    @Query("SELECT l FROM ProductPricingLink l " +
            "JOIN FETCH l.pricingComponent c " +
            "LEFT JOIN FETCH c.pricingTiers t " +
            "WHERE l.product.id = :productId " +
            "AND l.effectiveDate <= :targetDate " +
            "AND (l.expiryDate IS NULL OR l.expiryDate >= :targetDate)")
    List<ProductPricingLink> findByProductIdAndDate(@Param("productId") Long productId,
                                                    @Param("targetDate") LocalDate targetDate);

    List<ProductPricingLink> findByProductId(Long productId);

    long countByPricingComponentId(Long pricingComponentId);

    @Modifying
    @Transactional
    void deleteByPricingComponentId(Long pricingComponentId);
}