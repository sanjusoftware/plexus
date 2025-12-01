package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.ProductBundle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductBundleRepository extends JpaRepository<ProductBundle, Long> {

    /**
     * Finds a ProductBundle by its unique code.
     * The Hibernate Filter will automatically scope this search by the current bankId.
     * @param code The unique code of the product bundle.
     * @return An Optional containing the bundle.
     */
    Optional<ProductBundle> findByCode(String code);
}