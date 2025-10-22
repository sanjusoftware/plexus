package com.bankengine.catalog.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_feature_link")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductFeatureLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne
    @JoinColumn(name = "feature_component_id", nullable = false)
    private FeatureComponent featureComponent;

    // This is the product-specific value for the feature.
    // e.g., "120" if feature_component is "Max_Tenure" (DataType: INTEGER)
    @Column(name = "feature_value", nullable = false, length = 1000)
    private String featureValue;
}