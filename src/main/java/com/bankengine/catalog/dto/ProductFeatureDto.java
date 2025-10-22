// src/main/java/com/bankengine/catalog/dto/ProductFeatureDto.java
package com.bankengine.catalog.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO to handle a request to link a FeatureComponent to a Product.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductFeatureDto {

    // The ID of the existing Product
    private Long productId;

    // The ID of the existing FeatureComponent
    private Long featureComponentId;

    // The specific value for this product (e.g., "120" for Max_Tenure)
    private String featureValue;
}