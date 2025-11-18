package com.bankengine.pricing.converter;

import com.bankengine.pricing.dto.PricingInputMetadataDto;
import com.bankengine.pricing.model.PricingInputMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface PricingInputMetadataMapper {

    PricingInputMetadataMapper INSTANCE = Mappers.getMapper(PricingInputMetadataMapper.class);

    PricingInputMetadataDto toDto(PricingInputMetadata pricingInputMetadata);

    PricingInputMetadata toEntity(PricingInputMetadataDto pricingInputMetadataDto);
}
