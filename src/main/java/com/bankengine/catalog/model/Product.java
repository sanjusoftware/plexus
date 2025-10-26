package com.bankengine.catalog.model;

import com.bankengine.common.model.AuditableEntity;
import com.bankengine.pricing.model.ProductPricingLink;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Product extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // e.g., "Premier Home Loan"

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @ManyToOne // Many Products belong to one ProductType
    @JoinColumn(name = "product_type_id", nullable = false)
    private ProductType productType;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ProductFeatureLink> productFeatureLinks = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ProductPricingLink> productPricingLinks = new ArrayList<>();

    private String bankId;
    private LocalDate effectiveDate;
    private String status; // e.g., "ACTIVE", "DRAFT", "INACTIVE"
}