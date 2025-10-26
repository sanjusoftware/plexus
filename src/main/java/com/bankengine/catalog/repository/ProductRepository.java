package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    // Custom method to find products by their status (e.g., "ACTIVE")
    List<Product> findByStatus(String status);

    // Custom method to find products belonging to a specific bank
    List<Product> findByBankId(String bankId);

    // Custom finder method required by the TestDataSeeder
    Optional<Product> findByName(String name);
}