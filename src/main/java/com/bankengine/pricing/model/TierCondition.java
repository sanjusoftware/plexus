package com.bankengine.pricing.model;

import com.bankengine.common.annotation.TenantEntity;
import com.bankengine.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tier_condition")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TenantEntity
public class TierCondition extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The Pricing Tier this condition belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pricing_tier_id", nullable = false)
    private PricingTier pricingTier;

    // 1. The Attribute (Field) to check in the PricingInput object
    // e.g., "segment", "amount", "clientType", "channel"
    @Column(nullable = false)
    private String attributeName;

    // 2. The Operator (Dropdown in UI)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Operator operator;

    // 3. The Value (Input field in UI)
    // e.g., "PREMIUM", "500000", "true"
    @Column(nullable = false)
    private String attributeValue;

    // 4. The Logical Connector (For combining multiple conditions)
    // e.g., AND, OR (only applicable if there is a next condition)
    @Enumerated(EnumType.STRING)
    private LogicalConnector connector;

    public enum Operator {
        EQ, // == (Equal)
        NE, // != (Not Equal)
        GT, // > (Greater Than)
        GE, // >= (Greater or Equal)
        LT, // < (Less Than)
        LE, // <= (Less or Equal)
        IN // In a list (e.g., segment IN ('A', 'B'))
    }

    public enum LogicalConnector {
        AND, OR
    }

}