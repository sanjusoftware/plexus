package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductPricingDto;
import com.bankengine.catalog.dto.ProductPricingLinkDto;
import com.bankengine.pricing.model.ProductPricingLink;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PricingLinkMapper {

    @Mapping(target = "pricingComponentName", source = "link.pricingComponent.name")
    @Mapping(target = "context", source = "link.context")
    ProductPricingLinkDto toPricingLinkDto(ProductPricingLink link);

    List<ProductPricingLinkDto> toPricingLinkDtoList(List<ProductPricingLink> links);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProductPricingLink clone(ProductPricingLink oldLink);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "pricingComponent", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProductPricingLink toEntity(ProductPricingDto dto);
}