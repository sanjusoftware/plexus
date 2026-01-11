package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.BundleProductLink;
import com.bankengine.common.repository.TenantRepository;

import java.util.Optional;

public interface BundleProductLinkRepository extends TenantRepository<BundleProductLink, Long> {

    /**
     * Finds a BundleProductLink by its linked Product ID.
     * Used to check if a product is already assigned to a bundle.
     * @param productId The ID of the Product being checked.
     * @return An Optional containing the link if found.
     */
    Optional<BundleProductLink> findByProductId(Long productId);
}