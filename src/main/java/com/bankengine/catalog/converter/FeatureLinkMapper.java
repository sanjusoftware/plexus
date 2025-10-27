package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductFeatureLinkDto;
import com.bankengine.catalog.model.ProductFeatureLink;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface FeatureLinkMapper {

    @Mapping(target = "featureName", source = "link.featureComponent.name")
    ProductFeatureLinkDto toFeatureLinkDto(ProductFeatureLink link);

    List<ProductFeatureLinkDto> toFeatureLinkDtoList(List<ProductFeatureLink> links);
}