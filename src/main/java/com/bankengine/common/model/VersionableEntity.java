package com.bankengine.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder
public abstract class VersionableEntity extends AuditableEntity {

    @Column(nullable = false)
    protected String name;

    @Column(name = "code", nullable = false, length = 100)
    protected String code;

    @Builder.Default
    @Column(name = "version", nullable = false)
    protected Integer version = 1;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    protected EntityStatus status = EntityStatus.DRAFT;

    public enum EntityStatus {
        DRAFT,
        ACTIVE,
        INACTIVE,
        ARCHIVED
    }

    public boolean isDraft() {
        return EntityStatus.DRAFT.equals(this.status);
    }

    public boolean isActive() {
        return EntityStatus.ACTIVE.equals(this.status);
    }

    public boolean isInActive() {
        return EntityStatus.INACTIVE.equals(this.status);
    }

    public boolean isArchived() {
        return EntityStatus.ARCHIVED.equals(this.status);
    }
}