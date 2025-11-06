package com.bankengine.pricing.model;

import com.bankengine.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pricing_component")
@Getter
@Setter
// Define EntityGraph to enable multi-level eager fetching
@NamedEntityGraph(
        name = "component-with-tiers-values-conditions",
        attributeNodes = {
                // Start by fetching the List of Tiers (pricingTiers)
                @NamedAttributeNode(value = "pricingTiers", subgraph = "tier-subgraph")
        },
        subgraphs = {
                // Define how the Tier's sub-collections are fetched
                @NamedSubgraph(
                        name = "tier-subgraph",
                        attributeNodes = {
                                // Fetch the sub-collections needed for rule building
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
}