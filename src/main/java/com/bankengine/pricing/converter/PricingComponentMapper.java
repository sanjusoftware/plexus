package com.bankengine.pricing.converter;

import com.bankengine.config.MapStructConfig;
import com.bankengine.pricing.dto.*;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface PricingComponentMapper {

    PriceValueResponseDto toPriceValueDto(PriceValue entity);

    // MapStruct automatically maps the nested List<PriceValue> to List<PriceValueResponseDto>
    @Mapping(target = "priceValues", source = "priceValues")
    PricingTierResponseDto toPricingTierDto(PricingTier entity);

    @Mapping(target = "pricingTiers", source = "pricingTiers")
    PricingComponentResponseDto toResponseDto(PricingComponent entity);
    List<PricingComponentResponseDto> toResponseDtoList(List<PricingComponent> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "pricingTiers", ignore = true)
    PricingComponent toEntity(CreatePricingComponentRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "pricingTiers", ignore = true)
    void updateFromDto(UpdatePricingComponentRequestDto dto, @MappingTarget PricingComponent entity);
}