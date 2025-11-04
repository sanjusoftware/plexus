package com.bankengine.pricing.converter;

import com.bankengine.pricing.dto.CreatePricingTierRequestDto;
import com.bankengine.pricing.dto.UpdatePricingTierRequestDto;
import com.bankengine.pricing.model.PricingTier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {PriceValueMapper.class})
public interface PricingTierMapper {

    // For addTierAndValue
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "pricingComponent", ignore = true)
    @Mapping(target = "priceValues", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PricingTier toEntity(CreatePricingTierRequestDto dto);

    // For updateTierAndValue
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "pricingComponent", ignore = true)
    @Mapping(target = "priceValues", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromDto(UpdatePricingTierRequestDto dto, @MappingTarget PricingTier entity);
}