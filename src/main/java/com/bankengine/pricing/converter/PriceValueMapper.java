package com.bankengine.pricing.converter;

import com.bankengine.pricing.dto.CreatePriceValueRequestDto;
import com.bankengine.pricing.dto.PriceValueResponseDto;
import com.bankengine.pricing.dto.UpdatePriceValueRequestDto;
import com.bankengine.pricing.model.PriceValue;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PriceValueMapper {

    // For addTierAndValue
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "pricingTier", ignore = true) // Set manually in service
    @Mapping(target = "valueType", ignore = true)   // Set manually in service due to UPPERCASE logic
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PriceValue toEntity(CreatePriceValueRequestDto dto);

    // For updateTierAndValue (update)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "pricingTier", ignore = true)
    @Mapping(target = "valueType", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromDto(UpdatePriceValueRequestDto dto, @MappingTarget PriceValue entity);

    // For updateTierAndValue (response) and addTierAndValue (response)
    @Mapping(target = "valueType", source = "valueType") // MapStruct can map Enum to String via toString/name()
    PriceValueResponseDto toResponseDto(PriceValue entity);
}