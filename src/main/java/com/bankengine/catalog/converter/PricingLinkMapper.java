package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductPricingDto;
import com.bankengine.common.mapping.ToAuditableEntity;
import com.bankengine.config.MapStructConfig;
import com.bankengine.pricing.model.ProductPricingLink;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(config = MapStructConfig.class, componentModel = "spring")
public interface PricingLinkMapper {

    @Mapping(target = "pricingComponentName", source = "link.pricingComponent.name")
    @Mapping(target = "pricingComponentCode", source = "link.pricingComponent.code")
    @Mapping(target = "targetComponentCode", source = "link.targetComponentCode")
    ProductPricingDto toResponse(ProductPricingLink link);

    List<ProductPricingDto> toResponseList(List<ProductPricingLink> links);

    @ToAuditableEntity
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "pricingComponent", source = "oldLink.pricingComponent")
    @Mapping(target = "effectiveDate", ignore = true)
    @Mapping(target = "expiryDate", ignore = true)
    ProductPricingLink clone(ProductPricingLink oldLink);

    @ToAuditableEntity
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "pricingComponent", ignore = true)
    ProductPricingLink toEntity(ProductPricingDto dto);

    @ToAuditableEntity
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "pricingComponent", ignore = true)
    void updateFromDto(ProductPricingDto dto, @MappingTarget ProductPricingLink entity);
}