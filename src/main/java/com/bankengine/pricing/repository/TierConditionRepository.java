package com.bankengine.pricing.repository;

import com.bankengine.pricing.model.TierCondition;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository for managing TierCondition entities.
 */
@Repository
public interface TierConditionRepository extends JpaRepository<TierCondition, Long> {
    /**
     * Deletes all TierCondition entities associated with the given Pricing Tier ID.
     * Must be marked @Transactional because it performs a bulk modifying operation.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM TierCondition tc WHERE tc.pricingTier.id = ?1")
    void deleteByPricingTierId(Long pricingTierId);
}