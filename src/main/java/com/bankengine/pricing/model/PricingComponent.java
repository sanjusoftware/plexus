package com.bankengine.pricing.model;

import com.bankengine.common.annotation.TenantEntity;
import com.bankengine.common.model.VersionableEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pricing_component", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"bank_id", "code", "version"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
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
@TenantEntity
public class PricingComponent extends VersionableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    private String description;

    @Enumerated(EnumType.STRING)
    private ComponentType type;

    @Builder.Default
    private boolean proRataApplicable = false;

    @OneToMany(mappedBy = "pricingComponent", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PricingTier> pricingTiers = new ArrayList<>();

    public enum ComponentType {
        FEE, INTEREST_RATE, WAIVER, BENEFIT, DISCOUNT, PACKAGE_FEE, TAX
    }
}