package com.bankengine.common.model;

import com.bankengine.auth.security.BankContextHolder;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@FilterDef(name = "bankTenantFilter", parameters = @ParamDef(name = "bankId", type = String.class))
@Filter(name = "bankTenantFilter", condition = "bank_id = :bankId")
public abstract class AuditableEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "bank_id", nullable = false, updatable = false, length = 50)
    protected String bankId;

    /**
     * Ensures bank_id is always set before persisting,
     * regardless of whether it's a web request or a data seeder.
     */
    @PrePersist
    public void prePersist() {
        if (this.bankId == null) {
            this.bankId = BankContextHolder.getBankId();
        }
    }
}