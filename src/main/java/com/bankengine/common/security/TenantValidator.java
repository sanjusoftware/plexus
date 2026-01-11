package com.bankengine.common.security;

import com.bankengine.common.model.AuditableEntity;
import com.bankengine.web.exception.NotFoundException;
import org.springframework.stereotype.Component;

@Component
public class TenantValidator {

    /**
     * Validates that the entity belongs to the current bank context.
     * Throws NotFoundException if there is a mismatch to prevent ID probing.
     */
    public void validateOwnership(AuditableEntity entity, Long id, String bankId) {
        if (entity == null || !entity.getBankId().equals(bankId)) {
            // We use the same message for "not in DB" and "not in my bank"
            // to prevent leaking info about other tenants.
            throw new NotFoundException("Resource not found with ID: " + id);
        }
    }
}