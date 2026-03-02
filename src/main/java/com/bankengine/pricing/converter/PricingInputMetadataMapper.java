package com.bankengine.pricing.converter;

import com.bankengine.common.mapping.ToAuditableEntity;
import com.bankengine.config.MapStructConfig;
import com.bankengine.pricing.dto.PricingMetadataDto;
import com.bankengine.pricing.model.PricingInputMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class, componentModel = "spring")
public interface PricingInputMetadataMapper {

    PricingMetadataDto toResponse(PricingInputMetadata pricingInputMetadata);

    @ToAuditableEntity
    PricingInputMetadata toEntity(PricingMetadataDto pricingMetadataDto);

    @ToAuditableEntity
    void updateFromDto(PricingMetadataDto dto, @MappingTarget PricingInputMetadata entity);

    @ToAuditableEntity
    PricingInputMetadata clone(PricingInputMetadata source);
}