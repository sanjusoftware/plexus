package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.Product;
import com.bankengine.common.repository.VersionableRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends VersionableRepository<Product> {
}