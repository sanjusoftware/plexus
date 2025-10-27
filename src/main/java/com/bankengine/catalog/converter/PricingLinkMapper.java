package com.bankengine.catalog.converter;

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
}