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

    // Optional: A field to define the context/purpose (e.g., 'BUNDLE_FEE_CORE')
    private String context;

    /**
     * Stores the direct fixed value if rules are not used.
     */
    @Column(name = "fixed_value")
    private BigDecimal fixedValue;

    /**
     * If true, the pricing is determined by the Drools Rules Engine.
     */
    @Column(name = "use_rules_engine", nullable = false)
    private boolean useRulesEngine = false;

    public BundlePricingLink(ProductBundle productBundle, PricingComponent pricingComponent, String context, BigDecimal fixedValue, boolean useRulesEngine) {
        this.productBundle = productBundle;
        this.pricingComponent = pricingComponent;
        this.context = context;
        this.fixedValue = fixedValue;
        this.useRulesEngine = useRulesEngine;
    }
}