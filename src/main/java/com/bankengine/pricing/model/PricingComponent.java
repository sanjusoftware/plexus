package com.bankengine.pricing.model;

import com.bankengine.common.model.AuditableEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "pricing_component")
@Getter
@Setter
public class PricingComponent extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // e.g., "Annual Fee", "Base Interest Rate", "ATM Withdrawal Fee"

    @Enumerated(EnumType.STRING)
    private ComponentType type; // e.g., FEE, RATE, WAIVER, BENEFIT

    @OneToMany(mappedBy = "pricingComponent", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JsonIgnore // Prevents infinite recursion in case this component is serialized elsewhere
    private List<PricingTier> pricingTiers;

    public enum ComponentType {
        FEE, RATE, WAIVER, BENEFIT, DISCOUNT
    }
}