package com.bankengine.pricing.model;

import com.bankengine.catalog.model.Product;
import com.bankengine.common.annotation.TenantEntity;
import com.bankengine.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "product_pricing_link")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TenantEntity
public class ProductPricingLink extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Links to the specific pricing component (e.g., "Interest Rate", "Transaction Fee")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pricing_component_id", nullable = false)
    private PricingComponent pricingComponent;

    /**
     * Stores the direct value (e.g., the amount of an annual fee or a fixed rate).
     * Used for simple pricing components that do not require the Rules Engine.
     */
    @Column(name = "fixed_value")
    private BigDecimal fixedValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "fixed_value_type")
    private PriceValue.ValueType fixedValueType;

    /**
     * The code of the component this discount/fee targets
     * (used for specific discounts, e.g., 10% discount on maintenance fee of an account).
     */
    @Column(name = "target_component_code")
    private String targetComponentCode;

    /**
     * If true, the pricing is determined by the Drools Rules Engine.
     * If false, the price is the fixedValue.
     */
    @Column(name = "use_rules_engine", nullable = false)
    private boolean useRulesEngine = false;

    public ProductPricingLink(Product product, PricingComponent pricingComponent,
                              BigDecimal fixedValue, PriceValue.ValueType fixedValueType,
                              String targetComponentCode, boolean useRulesEngine) {
        this.product = product;
        this.pricingComponent = pricingComponent;
        this.fixedValue = fixedValue;
        this.fixedValueType = fixedValueType;
        this.targetComponentCode = targetComponentCode;
        this.useRulesEngine = useRulesEngine;
    }
}