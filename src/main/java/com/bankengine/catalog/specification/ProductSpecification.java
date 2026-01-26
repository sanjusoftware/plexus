package com.bankengine.catalog.specification;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.dto.ProductSearchRequest;
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
    public static Specification<Product> filterBy(ProductSearchRequest criteria) {
        return (Root<Product> root, CriteriaQuery<?> query, CriteriaBuilder builder) -> {
            List<Predicate> predicates = new ArrayList<>();

        // 1. MANDATORY: Tenant Isolation (The Safety Net)
        predicates.add(builder.equal(root.get("bankId"), TenantContextHolder.getBankId()));

        // 2. Name Search (Case-Insensitive LIKE)
        if (criteria.getName() != null && !criteria.getName().isBlank()) {
            predicates.add(builder.like(builder.lower(root.get("name")),
                "%" + criteria.getName().toLowerCase() + "%"));
        }

        // 3. Status
        if (criteria.getStatus() != null && !criteria.getStatus().isBlank()) {
            predicates.add(builder.equal(root.get("status"), criteria.getStatus()));
        }

        // 4. Category (If added to DTO)
        if (criteria.getCategory() != null && !criteria.getCategory().isBlank()) {
            predicates.add(builder.equal(root.get("category"), criteria.getCategory()));
        }

        // 5. Product Type
        if (criteria.getProductTypeId() != null) {
            predicates.add(builder.equal(root.get("productType").get("id"), criteria.getProductTypeId()));
        }

        // 6. Date Range Logic
        if (criteria.getEffectiveDateFrom() != null) {
            predicates.add(builder.greaterThanOrEqualTo(root.get("effectiveDate"), criteria.getEffectiveDateFrom()));
        }
        if (criteria.getEffectiveDateTo() != null) {
            predicates.add(builder.lessThanOrEqualTo(root.get("effectiveDate"), criteria.getEffectiveDateTo()));
        }

        return builder.and(predicates.toArray(new Predicate[0]));
    };
}
}