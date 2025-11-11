package com.bankengine.pricing.repository;

import com.bankengine.pricing.model.PricingInputMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PricingInputMetadataRepository extends JpaRepository<PricingInputMetadata, Long> {

    /**
     * Finds a single metadata record based on the unique attribute key (e.g., "customerSegment").
     */
    Optional<PricingInputMetadata> findByAttributeKey(String attributeKey);

    /**
     * Finds a list of metadata records whose keys are in the provided set.
     * Used by DroolsRuleBuilderService to efficiently load the cache before building rules.
     */
    List<PricingInputMetadata> findByAttributeKeyIn(Set<String> attributeKeys);
}