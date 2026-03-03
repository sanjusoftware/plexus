package com.bankengine.pricing.model;

import com.bankengine.common.annotation.TenantEntity;
import com.bankengine.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "pricing_tier")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@TenantEntity
public class PricingTier extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pricing_component_id", nullable = false)
    private PricingComponent pricingComponent;

    @Builder.Default
    @OneToMany(mappedBy = "pricingTier", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private Set<PriceValue> priceValues = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "pricingTier", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private Set<TierCondition> conditions = new HashSet<>();

    @Column(nullable = false)
    private String name;

    private BigDecimal minThreshold;
    private BigDecimal maxThreshold;

    // Identify if this tier allows pro-rating
    @Builder.Default
    private boolean proRataApplicable = false;

    // "Slab Breaching" flag
    // If true, the fee applies to the WHOLE transaction if the limit is breached
    @Builder.Default
    private boolean applyChargeOnFullBreach = false;

}