package com.bankengine.catalog.model;

import com.bankengine.common.annotation.TenantEntity;
import com.bankengine.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "bundle_product_link", uniqueConstraints = {
        // ensures a product is added only ONCE to a SPECIFIC bundle:
        @UniqueConstraint(
                name = "UK_bundle_product_link_unique",
                columnNames = {"product_bundle_id", "product_id"}
        )
})
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@TenantEntity
public class BundleProductLink extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Entity reference updated to ProductBundle
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_bundle_id", nullable = false)
    private ProductBundle productBundle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "is_main_account", nullable = false)
    @Builder.Default
    private boolean mainAccount = false; // Used for fee collection

    @Column(name = "is_mandatory", nullable = false)
    @Builder.Default
    private boolean mandatory = true; // Defines if the product must be included

}