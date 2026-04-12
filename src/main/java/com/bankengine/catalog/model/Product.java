package com.bankengine.catalog.model;

import com.bankengine.common.annotation.TenantEntity;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.pricing.model.ProductPricingLink;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"bank_id", "code", "version"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@TenantEntity
public class Product extends VersionableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;



    @Column(name = "category", nullable = false)
    @NotBlank(message = "Product category is mandatory for compatibility validation.")
    private String category; // e.g., "RETAIL", "WEALTH", "ISLAMIC"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(value = {
            @JoinColumn(name = "bank_id", referencedColumnName = "bank_id", insertable = false, updatable = false),
            @JoinColumn(name = "category", referencedColumnName = "code", insertable = false, updatable = false,
                    foreignKey = @ForeignKey(name = "fk_product_category_master"))
    })
    private ProductCategory categoryMaster;

    @ManyToOne
    @JoinColumn(name = "product_type_id", nullable = false)
    private ProductType productType;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProductFeatureLink> productFeatureLinks = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProductPricingLink> productPricingLinks = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<BundleProductLink> bundleLinks = new ArrayList<>();

    @Column(length = 100)
    private String tagline;

    @Column(length = 2000)
    private String fullDescription;

    @Column(length = 500)
    private String iconUrl;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "is_featured")
    private boolean featured;

    @Column(name = "target_customer_segments")
    private String targetCustomerSegments;

    @Column(name = "terms_and_conditions", columnDefinition = "TEXT")
    private String termsAndConditions;

}