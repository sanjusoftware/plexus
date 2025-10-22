package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.ProductFeatureLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductFeatureLinkRepository extends JpaRepository<ProductFeatureLink, Long> {

    // Method to fetch ALL feature links for a given Product ID
    List<ProductFeatureLink> findByProductId(Long productId);
}