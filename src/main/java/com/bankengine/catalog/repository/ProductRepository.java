package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.Product;
import com.bankengine.common.repository.VersionableRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends VersionableRepository<Product> {
	@Query("select distinct upper(trim(p.category)) from Product p where p.bankId = :bankId and p.category is not null and trim(p.category) <> ''")
	List<String> findDistinctCategoriesByBankId(@Param("bankId") String bankId);

	@Query("select count(p) > 0 from Product p where p.bankId = :bankId and upper(trim(p.category)) = :normalizedCategory")
	boolean existsByBankIdAndNormalizedCategory(@Param("bankId") String bankId,
	                                            @Param("normalizedCategory") String normalizedCategory);
}