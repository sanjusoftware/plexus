package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductFeature;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.config.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface FeatureLinkMapper {

    /**
     * Converts an entity to a Response DTO for API output.
     * Maps the nested FeatureComponent name to the flat featureName field.
     */
    @Mapping(target = "featureName", source = "link.featureComponent.name")
    ProductFeature toResponse(ProductFeatureLink link);

    List<ProductFeature> toResponseList(List<ProductFeatureLink> links);

    /**
     * Used for deep-cloning links during the product versioning process.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "bankId", source = "oldLink.bankId")
    ProductFeatureLink clone(ProductFeatureLink oldLink);

    /**
     * Converts a Request DTO to a new Entity.
     * Ignores relational and audit fields which are handled by the Service/JPA.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "featureComponent", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "bankId", ignore = true)
    ProductFeatureLink toEntity(ProductFeature dto);

    /**
     * Updates an existing link entity with values from a Request DTO.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "featureComponent", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "bankId", ignore = true)
    void updateFromDto(ProductFeature dto, @MappingTarget ProductFeatureLink entity);
}