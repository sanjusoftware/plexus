package com.bankengine.common.service;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.common.model.AuditableEntity;
import com.bankengine.common.repository.TenantRepository;
import com.bankengine.web.exception.NotFoundException;

public abstract class BaseService {

    protected String getCurrentBankId() {
        return TenantContextHolder.getBankId();
    }

    protected String getSystemBankId() {
        return TenantContextHolder.getSystemBankId();
    }

    /**
     * Generic helper to fetch an entity, check for existence, and validate tenant ownership.
     * @param repository The JPA repository for the entity
     * @param id The ID to look up
     * @param entityName Friendly name for the exception message (e.g., "Product")
     * @return The validated entity
     */
    protected <T extends AuditableEntity> T getByIdSecurely(TenantRepository<T, Long> repository, Long id, String entityName) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException(entityName + " not found with ID: " + id));
    }
}