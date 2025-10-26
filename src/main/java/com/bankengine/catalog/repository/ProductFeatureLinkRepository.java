package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.ProductFeatureLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductFeatureLinkRepository extends JpaRepository<ProductFeatureLink, Long> {

    // Method to fetch ALL feature links for a given Product ID
    List<ProductFeatureLink> findByProductId(Long productId);

    // Used by FeatureComponentService to check for dependencies
    boolean existsByFeatureComponentId(Long featureComponentId);

    // Used to return a helpful message showing the count of dependencies
    long countByFeatureComponentId(Long featureComponentId);

    // Used by integration tests to find links by FeatureComponent ID
    List<ProductFeatureLink> findByFeatureComponentId(Long featureComponentId);
}