package com.bankengine.pricing.model;

import com.bankengine.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pricing_component")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@NamedEntityGraph(
        name = "component-with-tiers-values-conditions",
        attributeNodes = {
                @NamedAttributeNode(value = "pricingTiers", subgraph = "tier-subgraph")
        },
        subgraphs = {
                @NamedSubgraph(
                        name = "tier-subgraph",
                        attributeNodes = {
                                @NamedAttributeNode("priceValues"),
                                @NamedAttributeNode("conditions")
                        }
                )
        }
)
public class PricingComponent extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // e.g., "Annual Fee", "Base Interest Rate", "ATM Withdrawal Fee"

    @Enumerated(EnumType.STRING)
    private ComponentType type; // e.g., FEE, RATE, WAIVER, BENEFIT

    @OneToMany(mappedBy = "pricingComponent", fetch = FetchType.LAZY)
    private List<PricingTier> pricingTiers = new ArrayList<>();

    public enum ComponentType {
        FEE, RATE, WAIVER, BENEFIT, DISCOUNT
    }

    public PricingComponent(String name, ComponentType type) {
        this.name = name;
        this.type = type;
    }
}