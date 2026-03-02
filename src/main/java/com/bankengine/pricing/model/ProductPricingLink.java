package com.bankengine.pricing.model;

import com.bankengine.catalog.model.Product;
import com.bankengine.common.annotation.TenantEntity;
import com.bankengine.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "product_pricing_link")
@Getter
@Setter
@SuperBuilder
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

    @Builder.Default
    @Column(name = "use_rules_engine", nullable = false)
    private boolean useRulesEngine = false;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

}