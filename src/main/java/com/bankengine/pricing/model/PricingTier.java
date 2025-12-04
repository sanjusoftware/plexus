package com.bankengine.pricing.model;

import com.bankengine.common.model.AuditableEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "pricing_tier")
@Getter
@Setter
@NoArgsConstructor
public class PricingTier extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id", nullable = false)
    private PricingComponent pricingComponent;

    @OneToMany(mappedBy = "pricingTier", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JsonIgnore
    private Set<PriceValue> priceValues = new HashSet<>();

    @OneToMany(mappedBy = "pricingTier", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private Set<TierCondition> conditions = new HashSet<>();

    private String tierName;

    // Numeric Tiers (e.g., Loan Amount Min/Max)
    private BigDecimal minThreshold;
    private BigDecimal maxThreshold;

    public PricingTier(PricingComponent pricingComponent, String tierName, BigDecimal minThreshold, BigDecimal maxThreshold) {
        this.pricingComponent = pricingComponent;
        this.tierName = tierName;
        this.minThreshold = minThreshold;
        this.maxThreshold = maxThreshold;
    }

}