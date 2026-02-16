package com.bankengine.common.repository;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.common.model.AuditableEntity;
import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

public class TenantRepositoryImpl<T extends AuditableEntity, ID extends Serializable>
        extends SimpleJpaRepository<T, ID> implements TenantRepository<T, ID> {

    private final EntityManager entityManager;

    public TenantRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
    }

    // 1. Single Fetch
    @Override
    public Optional<T> findById(ID id) {
        return findOne(idAndBankSpec(id));
    }

    @Override
    public Optional<T> findByBankId(String bankId) {
        return findOne(Specification.where(bankSpec())
                .and((root, query, cb) -> cb.equal(root.get("bankId"), bankId)));
    }


    // 2. Existence Check
    @Override
    public boolean existsById(ID id) {
        return count(idAndBankSpec(id)) > 0;
    }

    // 3. Bulk Fetch
    @Override
    public List<T> findAll() {
        return findAll(bankSpec());
    }

    /**
     * Reusable Specification to avoid boilerplate.
     * This ensures bank_id = currentBankId is ALWAYS in the WHERE clause,
     * unless the user is a SYSTEM admin.
     */
    private Specification<T> bankSpec() {
        return (root, query, cb) -> {
            String bankId = TenantContextHolder.getBankId();
            if (TenantContextHolder.getSystemBankId().equals(bankId)) {
                return cb.conjunction();
            }
            return cb.equal(root.get("bankId"), bankId);
        };
    }

    private Specification<T> idAndBankSpec(ID id) {
        return (root, query, cb) -> {
            String bankId = TenantContextHolder.getBankId();
            if (TenantContextHolder.getSystemBankId().equals(bankId)) {
                return cb.equal(root.get("id"), id);
            }
            return cb.and(cb.equal(root.get("id"), id),
                    cb.equal(root.get("bankId"), bankId));
        };
    }
}