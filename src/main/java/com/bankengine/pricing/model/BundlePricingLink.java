package com.bankengine.pricing.model;

import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.common.annotation.TenantEntity;
import com.bankengine.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

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
@SuperBuilder
@TenantEntity
public class BundlePricingLink extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_bundle_id", nullable = false)
    private ProductBundle productBundle;

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
    @Builder.Default
    private boolean useRulesEngine = false;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

}