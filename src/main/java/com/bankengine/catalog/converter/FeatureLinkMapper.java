package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductFeatureDto;
import com.bankengine.catalog.dto.ProductFeatureLinkDto;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.config.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface FeatureLinkMapper {

    @Mapping(target = "featureName", source = "link.featureComponent.name")
    ProductFeatureLinkDto toFeatureLinkDto(ProductFeatureLink link);

    List<ProductFeatureLinkDto> toFeatureLinkDtoList(List<ProductFeatureLink> links);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProductFeatureLink clone(ProductFeatureLink oldLink);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "featureComponent", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProductFeatureLink toEntity(ProductFeatureDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "featureComponent", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromDto(ProductFeatureDto dto, @MappingTarget ProductFeatureLink entity);
}