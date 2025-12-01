package com.bankengine.catalog.model;

import com.bankengine.common.model.AuditableEntity;
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

    // OneToMany link to the products contained within this bundle
    // MappedBy field updated to reference 'productBundle' in the link entity
    @OneToMany(mappedBy = "productBundle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BundleProductLink> containedProducts = new ArrayList<>();
}