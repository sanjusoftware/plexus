package com.bankengine.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;

@NoRepositoryBean
public interface TenantRepository<T, ID> extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {
    // We override the standard findById to ensure it's always tenant-aware
    @Override
    Optional<T> findById(ID id);

    Optional<T> findByBankId(String bankId);
}