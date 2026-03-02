package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.common.repository.VersionableRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductBundleRepository extends VersionableRepository<ProductBundle> {
}