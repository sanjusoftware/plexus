package com.bankengine.catalog.model;

import com.bankengine.common.annotation.TenantEntity;
import com.bankengine.common.model.AuditableEntity;
import com.bankengine.pricing.model.ProductPricingLink;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"bank_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TenantEntity
public class Product extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // e.g., "Premier Home Loan"

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    private LocalDate effectiveDate;
    private String status; // e.g., "ACTIVE", "DRAFT", "INACTIVE"

    @Column(name = "category", nullable = false)
    @NotBlank(message = "Product category is mandatory for compatibility validation.")
    private String category; // e.g., "RETAIL", "WEALTH", "ISLAMIC"

    @ManyToOne // Many Products belong to one ProductType
    @JoinColumn(name = "product_type_id", nullable = false)
    private ProductType productType;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ProductFeatureLink> productFeatureLinks = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProductPricingLink> productPricingLinks = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<BundleProductLink> bundleLinks = new ArrayList<>();

    @Column(length = 100)
    private String tagline; // Marketing headline

    @Column(length = 2000)
    private String fullDescription; // Rich text description

    @Column(length = 500)
    private String iconUrl; // Icon for UI display

    @Column(name = "display_order")
    private Integer displayOrder; // For sorting in catalog

    @Column(name = "is_featured")
    private boolean isFeatured; // For homepage highlights

    @Column(name = "target_customer_segments")
    private String targetCustomerSegments; // Comma-separated: "RETAIL,PREMIUM"

    @Column(name = "terms_and_conditions", columnDefinition = "TEXT")
    private String termsAndConditions;

}