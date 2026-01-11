package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductFeature;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.common.mapping.ToAuditableEntity;
import com.bankengine.config.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface FeatureLinkMapper {

    @Mapping(target = "featureName", source = "link.featureComponent.name")
    @Mapping(target = "featureComponentId", source = "link.featureComponent.id")
    ProductFeature toResponse(ProductFeatureLink link);

    List<ProductFeature> toResponseList(List<ProductFeatureLink> links);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    ProductFeatureLink clone(ProductFeatureLink oldLink);

    @ToAuditableEntity
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "featureComponent", ignore = true)
    ProductFeatureLink toEntity(ProductFeature dto);

    @ToAuditableEntity
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "featureComponent", ignore = true)
    void updateFromDto(ProductFeature dto, @MappingTarget ProductFeatureLink entity);
}