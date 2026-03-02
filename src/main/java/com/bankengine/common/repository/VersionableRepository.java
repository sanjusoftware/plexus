package com.bankengine.common.repository;

import com.bankengine.common.model.VersionableEntity;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface VersionableRepository<T extends VersionableEntity> extends TenantRepository<T, Long> {
    boolean existsByNameAndBankId(String name, String bankId);
    boolean existsByBankIdAndCodeAndVersion(String bankId, String code, Integer version);
}