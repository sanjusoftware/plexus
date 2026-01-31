package com.bankengine.pricing.model;

import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.common.annotation.TenantEntity;
import com.bankengine.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Links a PricingComponent to a ProductBundle. This allows applying fees or
 * discounts at the bundle level, separate from the products contained within.
 */
@Entity
@Table(name = "bundle_pricing_link")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TenantEntity
public class BundlePricingLink extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Link to the ProductBundle
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_bundle_id", nullable = false)
    private ProductBundle productBundle;

    // Links to the specific pricing component (e.g., "Monthly Package Fee")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pricing_component_id", nullable = false)
    private PricingComponent pricingComponent;

    /**
     * Stores the direct fixed value if rules are not used.
     */
    @Column(name = "fixed_value")
    private BigDecimal fixedValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "fixed_value_type")
    private PriceValue.ValueType fixedValueType;

    /**
     * If true, the pricing is determined by the Drools Rules Engine.
     */
    @Column(name = "use_rules_engine", nullable = false)
    private boolean useRulesEngine = false;

    /**
     * Effective date when the bundle pricing configuration becomes active.
     */
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    /**
     * Date on which the bundle pricing configuration ceases to exist.
     */
    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @PrePersist
    protected void onCreate() {
        if (this.effectiveDate == null) {
            this.effectiveDate = LocalDate.now();
        }
        if (this.expiryDate == null) {
            this.expiryDate = this.effectiveDate.plusYears(1);
        }
    }

    public BundlePricingLink(ProductBundle productBundle, PricingComponent pricingComponent,
                             BigDecimal fixedValue, PriceValue.ValueType fixedValueType, boolean useRulesEngine) {
        this.productBundle = productBundle;
        this.pricingComponent = pricingComponent;
        this.fixedValue = fixedValue;
        this.fixedValueType = fixedValueType;
        this.useRulesEngine = useRulesEngine;
        this.effectiveDate = LocalDate.now();
        this.expiryDate = this.effectiveDate.plusYears(1);
    }
}