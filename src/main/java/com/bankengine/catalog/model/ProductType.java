package com.bankengine.catalog.model;

import com.bankengine.common.annotation.TenantEntity;
import com.bankengine.common.model.AuditableEntity;
import com.bankengine.common.model.VersionableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "product_type", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"bank_id", "code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@TenantEntity
public class ProductType extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // e.g., "Loan", "Credit Card", "CASA"

    @Column(nullable = false, length = 100)
    private String code;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'DRAFT'")
    private VersionableEntity.EntityStatus status = VersionableEntity.EntityStatus.DRAFT;
}