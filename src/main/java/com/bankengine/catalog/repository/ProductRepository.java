package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.Product;
import com.bankengine.common.repository.TenantRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends TenantRepository<Product, Long> {
    Optional<Product> findByName(String name);

}