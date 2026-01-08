package com.bankengine.catalog.model;

import com.bankengine.common.annotation.TenantEntity;
import com.bankengine.common.model.AuditableEntity;
import com.bankengine.pricing.model.BundlePricingLink;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product_bundle", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"bank_id", "code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TenantEntity
public class ProductBundle extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code; // User-entered package code (e.g., "SALARY_PKG_01")

    @Column(nullable = false)
    private String name; // e.g., "Premier Salary Bundle"

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private String eligibilitySegment; // e.g., "Retail", "SME"

    @Column(name = "activation_date", nullable = false)
    private LocalDate activationDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BundleStatus status = BundleStatus.DRAFT;

    public enum BundleStatus {
        DRAFT,
        ACTIVE,
        ARCHIVED
    }

    // OneToMany link to the products contained within this bundle
    // MappedBy field updated to reference 'productBundle' in the link entity
    @OneToMany(mappedBy = "productBundle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BundleProductLink> containedProducts = new ArrayList<>();

    // Link to pricing logic for the bundle itself (e.g., the monthly fee or discount)
    @OneToMany(mappedBy = "productBundle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BundlePricingLink> bundlePricingLinks = new ArrayList<>();
}