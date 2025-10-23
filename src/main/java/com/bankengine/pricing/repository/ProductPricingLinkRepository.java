package com.bankengine.pricing.repository;

import com.bankengine.pricing.model.ProductPricingLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductPricingLinkRepository extends JpaRepository<ProductPricingLink, Long> {

    // Crucial method: Find all pricing components linked to a specific product
    List<ProductPricingLink> findByProductId(Long productId);
}