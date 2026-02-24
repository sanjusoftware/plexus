package com.bankengine.pricing.converter;

import com.bankengine.common.mapping.ToAuditableEntity;
import com.bankengine.config.MapStructConfig;
import com.bankengine.pricing.dto.PricingComponentRequest;
import com.bankengine.pricing.dto.PricingComponentResponse;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingComponent.ComponentType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.List;

@Mapper(config = MapStructConfig.class, uses = {PricingTierMapper.class})
public interface PricingComponentMapper {

    PricingComponentResponse toResponseDto(PricingComponent entity);

    List<PricingComponentResponse> toResponseDtoList(List<PricingComponent> entities);

    @ToAuditableEntity
    @Mapping(target = "type", source = "type", qualifiedByName = "mapComponentType")
    @Mapping(target = "pricingTiers", ignore = true)
    PricingComponent toEntity(PricingComponentRequest dto);

    @ToAuditableEntity
    @Mapping(target = "type", source = "type", qualifiedByName = "mapComponentType")
    @Mapping(target = "pricingTiers", ignore = true)
    void updateFromDto(PricingComponentRequest dto, @MappingTarget PricingComponent entity);

    @Named("mapComponentType")
    default ComponentType mapComponentType(String type) {
        if (type == null) return null;
        try {
            return ComponentType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid component type provided: " + type);
        }
    }
}