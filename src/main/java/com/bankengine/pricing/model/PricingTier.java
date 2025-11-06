package com.bankengine.pricing.model;

import com.bankengine.common.model.AuditableEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "pricing_tier")
@Getter
@Setter
public class PricingTier extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id", nullable = false)
    private PricingComponent pricingComponent;

    // Bidirectional link to all price values in this tier
    @OneToMany(mappedBy = "pricingTier", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JsonIgnore
    private Set<PriceValue> priceValues = new HashSet<>();

    // Bidirectional link to all TierConditions for this tier
    // The DroolsRuleBuilderService will iterate over this list.
    @OneToMany(mappedBy = "pricingTier", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Set<TierCondition> conditions = new HashSet<>();

    private String tierName;

    // Numeric Tiers (e.g., Loan Amount Min/Max)
    private BigDecimal minThreshold;
    private BigDecimal maxThreshold;

    // Textual Conditions (Optional - for simple segmentation like region or client type)
    private String conditionKey;
    private String conditionValue;
}