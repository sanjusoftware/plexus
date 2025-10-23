package com.bankengine.pricing.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "pricing_tier")
@Getter
@Setter
public class PricingTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id", nullable = false)
    private PricingComponent pricingComponent; // Links to the component it modifies

    private String tierName; // e.g., "Tier 1: High Net Worth", "Loan Size: 500k-1M"

    // Numeric Tiers (e.g., Loan Amount Min/Max)
    private BigDecimal minThreshold;
    private BigDecimal maxThreshold;

    // Textual Conditions (Optional - for simple segmentation like region or client type)
    private String conditionKey;
    private String conditionValue;
}