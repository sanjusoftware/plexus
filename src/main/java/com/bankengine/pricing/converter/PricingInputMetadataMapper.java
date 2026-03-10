package com.bankengine.pricing.converter;

import com.bankengine.common.mapping.ToAuditableEntity;
import com.bankengine.config.MapStructConfig;
import com.bankengine.pricing.dto.PricingMetadataRequest;
import com.bankengine.pricing.dto.PricingMetadataResponse;
import com.bankengine.pricing.model.PricingInputMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class, componentModel = "spring")
public interface PricingInputMetadataMapper {

    PricingMetadataResponse toResponse(PricingInputMetadata pricingInputMetadata);

    @ToAuditableEntity
    @Mapping(target = "dataType", expression = "java(pricingMetadataDto.getDataType() != null ? pricingMetadataDto.getDataType().toUpperCase() : null)")
    PricingInputMetadata toEntity(PricingMetadataRequest pricingMetadataDto);

    @ToAuditableEntity
    void updateFromDto(PricingMetadataRequest dto, @MappingTarget PricingInputMetadata entity);

    @ToAuditableEntity
    PricingInputMetadata clone(PricingInputMetadata source);
}
