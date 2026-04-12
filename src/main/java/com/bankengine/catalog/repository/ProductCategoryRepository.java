package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.ProductCategory;
import com.bankengine.common.repository.TenantRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductCategoryRepository extends TenantRepository<ProductCategory, Long> {
    Optional<ProductCategory> findByBankIdAndCode(String bankId, String code);

    @Query("select c from ProductCategory c where c.bankId = :bankId order by c.name asc")
    List<ProductCategory> findAllByBankIdOrderByName(@Param("bankId") String bankId);

    @Query("select upper(trim(c.code)) from ProductCategory c where c.bankId = :bankId")
    List<String> findCategoryCodesByBankId(@Param("bankId") String bankId);
}
