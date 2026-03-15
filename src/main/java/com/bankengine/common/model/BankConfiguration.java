package com.bankengine.common.model;

import com.bankengine.common.annotation.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "bank_configuration",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_bank_id",
            columnNames = {"bank_id"}
        ),
        @UniqueConstraint(
            name = "uk_issuer_client_combination",
            columnNames = {"issuer_url", "client_id"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@TenantEntity
public class BankConfiguration extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "currency_code", nullable = false)
    @Builder.Default
    private String currencyCode = "NO_CURR";

    @Column(name = "allow_multi_bundle_product", nullable = false)
    @Builder.Default
    private boolean allowProductInMultipleBundles = false;

    @ElementCollection
    @CollectionTable(name = "bank_category_conflicts", joinColumns = @JoinColumn(name = "config_id"))
    @Builder.Default
    private List<CategoryConflictRule> categoryConflictRules = new ArrayList<>();

    @Column(name = "issuer_url", nullable = false)
    private String issuerUrl; // e.g., "https://login.microsoftonline.com/{tenantId}/v2.0"

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "client_secret")
    private String clientSecret;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private BankStatus status = BankStatus.DRAFT;

    @Column(name = "admin_name")
    private String adminName;

    @Column(name = "admin_email")
    private String adminEmail;

    @Column(name = "name")
    private String name;
}