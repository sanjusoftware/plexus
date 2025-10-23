package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductTypeRepository extends JpaRepository<ProductType, Long> {

    // Custom method to find a ProductType by its name, needed for seeding
    Optional<ProductType> findByName(String name);
}