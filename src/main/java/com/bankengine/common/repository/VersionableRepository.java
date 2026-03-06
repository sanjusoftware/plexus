package com.bankengine.common.repository;

import com.bankengine.common.model.VersionableEntity;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;

@NoRepositoryBean
public interface VersionableRepository<T extends VersionableEntity> extends TenantRepository<T, Long> {
    boolean existsByNameAndBankId(String name, String bankId);
    boolean existsByBankIdAndCodeAndVersion(String bankId, String code, Integer version);

    Optional<T> findByBankIdAndCodeAndVersion(String bankId, String code, Integer version);
    Optional<T> findFirstByBankIdAndCodeAndStatusOrderByVersionDesc(String bankId, String code, VersionableEntity.EntityStatus status);
    Optional<T> findFirstByBankIdAndCodeOrderByVersionDesc(String bankId, String code);
}