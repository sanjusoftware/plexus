package com.bankengine.catalog.model;

import com.bankengine.common.annotation.TenantEntity;
import com.bankengine.common.model.AuditableEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "product_category", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"bank_id", "code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@TenantEntity
public class ProductCategory extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String code;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String name;

    @Builder.Default
    @Column(nullable = false)
    private boolean archived = false;
}
