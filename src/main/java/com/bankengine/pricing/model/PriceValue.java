package com.bankengine.pricing.model;

import com.bankengine.common.annotation.TenantEntity;
import com.bankengine.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "price_value")
@Getter
@Setter
@NoArgsConstructor
@TenantEntity
public class PriceValue extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pricing_tier_id", nullable = false)
    private PricingTier pricingTier;

    @Column(nullable = false)
    private BigDecimal rawValue; // The actual fee amount or rate percentage

    @Enumerated(EnumType.STRING)
    private ValueType valueType; // ABSOLUTE (e.g., $50), PERCENTAGE (e.g., 2.5%)

    public enum ValueType {
        FEE_ABSOLUTE,
        FEE_PERCENTAGE,
        WAIVED,
        DISCOUNT_PERCENTAGE,
        DISCOUNT_ABSOLUTE,
        FREE_COUNT
    }

    public PriceValue(PricingTier pricingTier, BigDecimal rawValue, ValueType valueType) {
        this.pricingTier = pricingTier;
        this.rawValue = rawValue;
        this.valueType = valueType;
    }

    // ------------------------------------------------
    // 💡 REQUIRED FIELDS FOR DROOLS FACT INSERTION
    // ------------------------------------------------

    // Used by setMatchedTierId(Long)
    @Transient
    private Long matchedTierId;

    // Used by setComponentCode(String)
    @Transient
    private String componentCode;
}