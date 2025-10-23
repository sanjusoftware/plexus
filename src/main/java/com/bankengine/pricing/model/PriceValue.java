package com.bankengine.pricing.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "price_value")
@Getter
@Setter
public class PriceValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tier_id", nullable = false)
    private PricingTier pricingTier; // Links to the tier

    @Column(nullable = false)
    private BigDecimal priceAmount; // The actual fee amount or rate percentage

    @Enumerated(EnumType.STRING)
    private ValueType valueType; // ABSOLUTE (e.g., $50), PERCENTAGE (e.g., 2.5%)

    private String currency; // e.g., USD, EUR, INR

    public enum ValueType {
        ABSOLUTE, PERCENTAGE, WAIVED, DISCOUNT_PERCENTAGE, DISCOUNT_ABSOLUTE
    }
}