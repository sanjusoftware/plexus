package com.bankengine.pricing.converter;

import com.bankengine.config.MapStructConfig;
import com.bankengine.pricing.dto.PricingMetadataDto;
import com.bankengine.pricing.model.PricingInputMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface PricingInputMetadataMapper {

    /**
     * Maps the PricingInputMetadata entity to the response DTO.
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "attributeKey", source = "attributeKey")
    @Mapping(target = "displayName", source = "displayName")
    @Mapping(target = "dataType", source = "dataType")
    PricingMetadataDto toResponse(PricingInputMetadata pricingInputMetadata);

    /**
     * Maps the PricingInputMetadata entity to the request DTO.
     */
    @Mapping(target = "attributeKey", source = "attributeKey")
    @Mapping(target = "displayName", source = "displayName")
    @Mapping(target = "dataType", source = "dataType")
    PricingMetadataDto toCreateRequestDto(PricingInputMetadata pricingInputMetadata);

    // --- Mappings from DTO to Entity ---

    /**
     * Maps the creation request DTO to the entity for saving.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PricingInputMetadata toEntity(PricingMetadataDto pricingInputMetadataDto);
}