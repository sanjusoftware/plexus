package com.bankengine.pricing.model;

import com.bankengine.catalog.model.Product;
import com.bankengine.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "product_pricing_link")
@Getter
@Setter
public class ProductPricingLink extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Links to the specific product in the Catalog Service
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Links to the specific pricing component
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pricing_component_id", nullable = false)
    private PricingComponent pricingComponent;

    // Optional: A field to define the context/purpose (e.g., 'CORE_RATE', 'ANNUAL_FEE')
    private String context;
}