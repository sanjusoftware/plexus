package com.bankengine.pricing.model;

import com.bankengine.common.annotation.TenantEntity;
import com.bankengine.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "pricing_tier")
@Getter
@Setter
@NoArgsConstructor
@TenantEntity
public class PricingTier extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pricing_component_id", nullable = false)
    private PricingComponent pricingComponent;

    @OneToMany(mappedBy = "pricingTier", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
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

    // Support for "Rule changes applying only at next billing period"
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate = LocalDate.now();

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    // Identify if this tier allows pro-rating
    private boolean proRataApplicable = false;

    // "Slab Breaching" flag
    // If true, the fee applies to the WHOLE transaction if the limit is breached
    private boolean applyChargeOnFullBreach = false;

}