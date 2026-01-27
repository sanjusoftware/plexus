package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.BundleProductLink;
import com.bankengine.common.repository.TenantRepository;

import java.util.List;

public interface BundleProductLinkRepository extends TenantRepository<BundleProductLink, Long> {
    List<BundleProductLink> findAllByProductId(Long productId);
}