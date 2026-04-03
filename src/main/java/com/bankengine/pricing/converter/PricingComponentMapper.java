package com.bankengine.pricing.converter;

import com.bankengine.common.mapping.ToNewEntity;
import com.bankengine.common.mapping.ToVersionableEntity;
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

@Mapper(config = MapStructConfig.class, componentModel = "spring", uses = {PricingTierMapper.class})
public interface PricingComponentMapper {

    @Mapping(target = "version", source = "version")
    @Mapping(target = "status", source = "status")
    PricingComponentResponse toResponseDto(PricingComponent entity);

    List<PricingComponentResponse> toResponseDtoList(List<PricingComponent> entities);

    @ToNewEntity
    @Mapping(target = "type", source = "type", qualifiedByName = "mapComponentType")
    @Mapping(target = "pricingTiers", ignore = true)
    @Mapping(target = "activationDate", source = "activationDate")
    @Mapping(target = "expiryDate", source = "expiryDate")
    PricingComponent toEntity(PricingComponentRequest dto);

    @ToVersionableEntity
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "type", source = "type", qualifiedByName = "mapComponentType")
    @Mapping(target = "pricingTiers", ignore = true)
    @Mapping(target = "activationDate", source = "activationDate")
    @Mapping(target = "expiryDate", source = "expiryDate")
    void updateFromDto(PricingComponentRequest dto, @MappingTarget PricingComponent entity);

    @ToNewEntity
    @Mapping(target = "pricingTiers", source = "old.pricingTiers")
    PricingComponent clone(PricingComponent old);

    @Named("mapComponentType")
    default ComponentType mapComponentType(String type) {
        if (type == null) throw new IllegalArgumentException("Component type is required.");
        try {
            return ComponentType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value for pricing component type. Valid values are FEE, INTEREST_RATE, WAIVER, BENEFIT, DISCOUNT, PACKAGE_FEE, TAX");
        }
    }
}