package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.ProductType;
import com.bankengine.common.repository.TenantRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductTypeRepository extends TenantRepository<ProductType, Long> {
    Optional<ProductType> findByName(String name);
}