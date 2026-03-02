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

    public static Specification<Product> filterBy(ProductSearchRequest criteria) {
        return (Root<Product> root, CriteriaQuery<?> query, CriteriaBuilder builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Tenant Security (Bank ID)
            predicates.add(builder.equal(root.get("bankId"), TenantContextHolder.getBankId()));

            // 2. Metadata Filters
            if (criteria.getCode() != null && !criteria.getCode().isBlank()) {
                predicates.add(builder.equal(root.get("code"), criteria.getCode()));
            }

            if (criteria.getName() != null && !criteria.getName().isBlank()) {
                predicates.add(builder.like(builder.lower(root.get("name")),
                    "%" + criteria.getName().toLowerCase() + "%"));
            }

            if (criteria.getStatus() != null && !criteria.getStatus().isBlank()) {
                predicates.add(builder.equal(root.get("status"), criteria.getStatus()));
            }

            if (criteria.getCategory() != null && !criteria.getCategory().isBlank()) {
                predicates.add(builder.equal(root.get("category"), criteria.getCategory()));
            }

            // 3. Product Type Join
            if (criteria.getProductTypeId() != null) {
                predicates.add(builder.equal(root.get("productType").get("id"), criteria.getProductTypeId()));
            }

            // 4. Date Range
            if (criteria.getActivationDateFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("activationDate"), criteria.getActivationDateFrom()));
            }
            if (criteria.getActivationDateTo() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("activationDate"), criteria.getActivationDateTo()));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}