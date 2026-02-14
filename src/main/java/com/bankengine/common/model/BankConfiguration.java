package com.bankengine.common.model;

import com.bankengine.common.annotation.TenantEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "bank_configuration",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_bank_issuer_combination",
            columnNames = {"bank_id", "issuer_url"}
        ),
        @UniqueConstraint(
            name = "uk_bank_issuer_url",
            columnNames = {"issuer_url"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TenantEntity
public class BankConfiguration extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "allow_multi_bundle_product", nullable = false)
    private boolean allowProductInMultipleBundles = false;

    @ElementCollection
    @CollectionTable(name = "bank_category_conflicts", joinColumns = @JoinColumn(name = "config_id"))
    private List<CategoryConflictRule> categoryConflictRules = new ArrayList<>();

    @Column(name = "issuer_url", nullable = false)
    private String issuerUrl; // e.g., "https://login.microsoftonline.com/{tenantId}/v2.0"
}
