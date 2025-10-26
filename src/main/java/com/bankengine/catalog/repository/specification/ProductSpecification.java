package com.bankengine.catalog.repository.specification;

import com.bankengine.catalog.dto.ProductSearchRequestDto;
import com.bankengine.catalog.model.Product;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ProductSpecification {

    /**
     * Builds a composite Specification based on the provided search criteria DTO.
     */
    public static Specification<Product> filterBy(ProductSearchRequestDto criteria) {
        return (Root<Product> root, CriteriaQuery<?> query, CriteriaBuilder builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Name (Partial Match - LIKE)
            if (criteria.getName() != null && !criteria.getName().isBlank()) {
                predicates.add(builder.like(builder.lower(root.get("name")),
                        "%" + criteria.getName().toLowerCase() + "%"));
            }

            // 2. Bank ID (Exact Match)
            if (criteria.getBankId() != null && !criteria.getBankId().isBlank()) {
                predicates.add(builder.equal(root.get("bankId"), criteria.getBankId()));
            }

            // 3. Status (Exact Match)
            if (criteria.getStatus() != null && !criteria.getStatus().isBlank()) {
                predicates.add(builder.equal(root.get("status"), criteria.getStatus()));
            }

            // 4. Product Type ID (Join/Foreign Key Match)
            if (criteria.getProductTypeId() != null) {
                // We assume productType is the mapped field name
                predicates.add(builder.equal(root.get("productType").get("id"), criteria.getProductTypeId()));
            }

            // 5. Effective Date Range (Product's terms must be effective within the criteria window)
            // Example: Find products effective on or after 'effectiveDateFrom'
            if (criteria.getEffectiveDateFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("effectiveDate"), criteria.getEffectiveDateFrom()));
            }
            // Example: Find products effective on or before 'effectiveDateTo'
            if (criteria.getEffectiveDateTo() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("effectiveDate"), criteria.getEffectiveDateTo()));
            }

            // Combine all predicates with AND
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}