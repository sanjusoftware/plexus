package com.bankengine.common.service;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.dto.VersionRequest;
import com.bankengine.common.model.AuditableEntity;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.common.repository.TenantRepository;
import com.bankengine.common.repository.VersionableRepository;
import com.bankengine.web.exception.NotFoundException;
import org.springframework.security.access.AccessDeniedException;

import java.util.Objects;

public abstract class BaseService {

    protected String getCurrentBankId() {
        return TenantContextHolder.getBankId();
    }

    protected String getSystemBankId() {
        return TenantContextHolder.getSystemBankId();
    }

    /**
     * Generic helper to fetch an entity, check for existence, and validate tenant ownership.
     */
    protected <T extends AuditableEntity> T getByIdSecurely(TenantRepository<T, Long> repository, Long id, String entityName) {
        T entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(entityName + " not found with ID: " + id));

        if (!Objects.equals(entity.getBankId(), getCurrentBankId()) && !Objects.equals(entity.getBankId(), getSystemBankId())) {
            throw new AccessDeniedException("You do not have permission to access this " + entityName);
        }

        return entity;
    }

    protected <T extends VersionableEntity> T getByCodeAndVersionSecurely(
            VersionableRepository<T> repository,
            String code,
            Integer version,
            String entityName) {

        String bankId = getCurrentBankId();
        T entity;

        if (version != null) {
            entity = repository.findByBankIdAndCodeAndVersion(bankId, code, version)
                    .orElseThrow(() -> new NotFoundException(
                            String.format("%s not found with code: %s and version: %d", entityName, code, version)));
        } else {
            entity = repository.findFirstByBankIdAndCodeOrderByVersionDesc(bankId, code)
                    .orElseThrow(() -> new NotFoundException(
                            String.format("Latest %s not found with code: %s", entityName, code)));
        }

        return entity;
    }

    /**
     * Ensures the entity is in DRAFT status.
     */
    protected void validateDraft(VersionableEntity entity) {
        if (entity == null || !entity.isDraft()) {
            throw new IllegalStateException("Operation allowed only on DRAFT status.");
        }
    }

    /**
     * Creation validation: checks code uniqueness for version 1 within the current bank.
     */
    protected <T extends VersionableEntity> void validateNewVersionable(
            VersionableRepository<T> repository,
            String code) {

        String bankId = getCurrentBankId();

        if (repository.existsByBankIdAndCodeAndVersion(bankId, code, 1)) {
            throw new IllegalArgumentException("Entity code '" + code + "' version 1 already exists.");
        }
    }

    /**
     * Standardized preparation for new versions/branches.
     */
    protected <T extends VersionableEntity> void prepareNewVersion(
            T newEntity,
            T oldEntity,
            VersionRequest request,
            VersionableRepository<T> repository) {

        String bankId = getCurrentBankId();

        if (!Objects.equals(oldEntity.getBankId(), bankId)) {
            throw new AccessDeniedException("Unauthorized to version this entity.");
        }

        // Logic check: Is this a Revision (same lineage) or a Branch (new lineage)?
        boolean isNewCodeProvided = request.getNewCode() != null && !request.getNewCode().isBlank();
        boolean isSameCode = !isNewCodeProvided || request.getNewCode().equals(oldEntity.getCode());

        String targetCode;
        int targetVersion;
        VersionableEntity.EntityStatus targetStatus;

        if (isSameCode) {
            // --- REVISION (Same Code) ---
            // Creating a new version from an existing entity (DRAFT or ACTIVE)
            // always creates a new DRAFT to allow further editing.
            targetCode = oldEntity.getCode();
            targetVersion = oldEntity.getVersion() + 1;
            targetStatus = VersionableEntity.EntityStatus.DRAFT;
        } else {
            // --- REQUIREMENT 2: BRANCH (Different Code) ---
            targetCode = request.getNewCode();
            targetVersion = 1;
            targetStatus = VersionableEntity.EntityStatus.DRAFT; // Cloned as DRAFT

            // Source entity remains untouched (remains ACTIVE or whatever it was)
        }

        // Uniqueness Check
        if (repository.existsByBankIdAndCodeAndVersion(bankId, targetCode, targetVersion)) {
            throw new IllegalStateException(String.format(
                "Entity with code '%s' and version %d already exists.", targetCode, targetVersion));
        }

        // Apply metadata to the NEW cloned entity
        newEntity.setBankId(bankId);
        newEntity.setStatus(targetStatus);
        newEntity.setName(request.getNewName() != null && !request.getNewName().isBlank()
                ? request.getNewName() : oldEntity.getName());
        newEntity.setCode(targetCode);
        newEntity.setVersion(targetVersion);

        handleTemporalVersioning(newEntity, oldEntity, request);
    }

    protected <T extends VersionableEntity> void handleTemporalVersioning(T newEntity, T oldEntity, VersionRequest request) {
        // Default: No-op
    }
}