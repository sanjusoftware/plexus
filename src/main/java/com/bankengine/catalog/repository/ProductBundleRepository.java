package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.common.repository.TenantRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductBundleRepository extends TenantRepository<ProductBundle, Long> {
}