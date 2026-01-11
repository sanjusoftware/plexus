package com.bankengine.pricing.converter;

import com.bankengine.common.mapping.ToAuditableEntity;
import com.bankengine.config.MapStructConfig;
import com.bankengine.pricing.dto.PricingMetadataDto;
import com.bankengine.pricing.model.PricingInputMetadata;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface PricingInputMetadataMapper {

    /**
     * Maps the PricingInputMetadata entity to the response DTO.
     * All audit fields will flow through naturally if they exist in PricingMetadataDto.
     */
    PricingMetadataDto toResponse(PricingInputMetadata pricingInputMetadata);

    /**
     * Maps the PricingInputMetadata entity to a DTO for internal request use.
     */
    PricingMetadataDto toCreateRequestDto(PricingInputMetadata pricingInputMetadata);

    // --- Mappings from DTO to Entity ---

    /**
     * Maps the creation request DTO to the entity for saving.
     */
    @ToAuditableEntity
    PricingInputMetadata toEntity(PricingMetadataDto pricingInputMetadataDto);
}