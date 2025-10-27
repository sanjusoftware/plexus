package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductResponseDto;
import com.bankengine.catalog.dto.ProductFeatureLinkDto;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductFeatureLink;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.stream.Collectors;

@Component
public class ProductConverter {

    /**
     * Converts a Product entity to a ProductResponseDto.
     * This method handles the flattening of the ProductType relationship.
     */
    public ProductResponseDto convertToResponseDto(Product product) {
        ProductResponseDto dto = new ProductResponseDto();
        dto.setId(product.getId());
        dto.setName(product.getName());

        // Flattening ProductType: Consistent with your current DTO structure
        if (product.getProductType() != null) {
            dto.setProductTypeId(product.getProductType().getId());
            dto.setProductTypeName(product.getProductType().getName());
        } else {
            dto.setProductTypeId(null);
            dto.setProductTypeName(null);
        }

        dto.setBankId(product.getBankId());
        dto.setEffectiveDate(product.getEffectiveDate());
        dto.setStatus(product.getStatus());
        dto.setExpirationDate(product.getExpirationDate());
        dto.setCreatedAt(product.getCreatedAt());
        dto.setUpdatedAt(product.getUpdatedAt());

        if (product.getProductFeatureLinks() != null) {
            dto.setFeatures(
                    product.getProductFeatureLinks().stream()
                            .map(this::convertLinkToDto)
                            .collect(Collectors.toList())
            );
        } else {
            dto.setFeatures(Collections.emptyList());
        }

        return dto;
    }

    /**
     * Helper Method: Converts a ProductFeatureLink Entity to its DTO.
     */
    private ProductFeatureLinkDto convertLinkToDto(ProductFeatureLink link) {
        ProductFeatureLinkDto dto = new ProductFeatureLinkDto();

        // Ensure you fetch the component name, not the entire component object
        dto.setFeatureName(link.getFeatureComponent().getName());
        dto.setFeatureValue(link.getFeatureValue());

        return dto;
    }
}