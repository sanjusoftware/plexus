package com.bankengine.catalog.model;

import com.bankengine.common.annotation.TenantEntity;
import com.bankengine.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_feature_link", uniqueConstraints = {
        @UniqueConstraint(
                name = "UK_product_feature_link",
                columnNames = {"product_id", "feature_component_id"}
        )
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TenantEntity
/*
 For non-monetary configuration (e.g., "Color: Gold", "Max Tenure: 360").
 If it has a dollar sign, it should probably be a PricingComponent.
*/
public class ProductFeatureLink extends AuditableEntity {

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

    public ProductFeatureLink(Product product, FeatureComponent featureComponent, String featureValue) {
        this.product = product;
        this.featureComponent = featureComponent;
        this.featureValue = featureValue;
    }
}