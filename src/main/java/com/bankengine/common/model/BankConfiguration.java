package com.bankengine.common.model;

import com.bankengine.common.annotation.TenantEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bank_configuration")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@TenantEntity
public class BankConfiguration {

    @Id
    @Column(name = "bank_id", length = 50)
    private String bankId;

    @Column(name = "allow_multi_bundle_product", nullable = false)
    private boolean allowProductInMultipleBundles = false;

    @ElementCollection
    @CollectionTable(name = "bank_category_conflicts", joinColumns = @JoinColumn(name = "bank_id"))
    private List<CategoryConflictRule> categoryConflictRules = new ArrayList<>();

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
}
