package com.bankengine.pricing.repository;

import com.bankengine.common.repository.TenantRepository;
import com.bankengine.pricing.model.PricingInputMetadata;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PricingInputMetadataRepository extends TenantRepository<PricingInputMetadata, Long> {
    Optional<PricingInputMetadata> findByAttributeKey(String attributeKey);
    List<PricingInputMetadata> findByAttributeKeyIn(Set<String> attributeKeys);
    void deleteByAttributeKey(String attributeKey);
}