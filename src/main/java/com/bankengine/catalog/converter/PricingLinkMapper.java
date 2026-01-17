package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductPricing;
import com.bankengine.common.mapping.ToAuditableEntity;
import com.bankengine.config.MapStructConfig;
import com.bankengine.pricing.model.ProductPricingLink;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface PricingLinkMapper {

    @Mapping(target = "pricingComponentName", source = "link.pricingComponent.name")
    @Mapping(target = "pricingComponentId", source = "link.pricingComponent.id")
    ProductPricing toResponse(ProductPricingLink link);

    List<ProductPricing> toResponseList(List<ProductPricingLink> links);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    ProductPricingLink clone(ProductPricingLink oldLink);

    @ToAuditableEntity
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "pricingComponent", ignore = true)
    ProductPricingLink toEntity(ProductPricing dto);

    @ToAuditableEntity
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "pricingComponent", ignore = true)
    void updateFromDto(ProductPricing dto, @MappingTarget ProductPricingLink entity);
}