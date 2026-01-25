package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.BundlePricingResponse;
import com.bankengine.config.MapStructConfig;
import com.bankengine.pricing.model.BundlePricingLink;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface BundlePricingMapper {

    @Mapping(target = "pricingComponentName", source = "link.pricingComponent.name")
    @Mapping(target = "pricingComponentId", source = "link.pricingComponent.id")
    BundlePricingResponse toResponse(BundlePricingLink link);

    List<BundlePricingResponse> toResponseList(List<BundlePricingLink> links);
}
