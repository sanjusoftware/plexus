package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductFeatureDto;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.common.mapping.ToAuditableEntity;
import com.bankengine.config.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(config = MapStructConfig.class, componentModel = "spring")
public interface FeatureLinkMapper {

    @Mapping(target = "featureName", source = "link.featureComponent.name")
    @Mapping(target = "featureComponentCode", source = "link.featureComponent.code")
    ProductFeatureDto toResponse(ProductFeatureLink link);

    List<ProductFeatureDto> toResponseList(List<ProductFeatureLink> links);

    @ToAuditableEntity
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "featureComponent", source = "oldLink.featureComponent")
    ProductFeatureLink clone(ProductFeatureLink oldLink);

    @ToAuditableEntity
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "featureComponent", ignore = true)
    ProductFeatureLink toEntity(ProductFeatureDto dto);

    @ToAuditableEntity
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "featureComponent", ignore = true)
    void updateFromDto(ProductFeatureDto dto, @MappingTarget ProductFeatureLink entity);
}