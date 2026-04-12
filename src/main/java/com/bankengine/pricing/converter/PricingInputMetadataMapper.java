package com.bankengine.pricing.converter;

import com.bankengine.common.mapping.ToAuditableEntity;
import com.bankengine.config.MapStructConfig;
import com.bankengine.pricing.dto.PricingMetadataRequest;
import com.bankengine.pricing.dto.PricingMetadataResponse;
import com.bankengine.pricing.model.PricingInputMetadata;
import com.bankengine.pricing.service.PricingAttributeKeys;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

@Mapper(config = MapStructConfig.class, componentModel = "spring")
public interface PricingInputMetadataMapper {

    @Mapping(target = "system", source = "attributeKey", qualifiedByName = "isSystemAttribute")
    PricingMetadataResponse toResponse(PricingInputMetadata pricingInputMetadata);

    @Named("isSystemAttribute")
    default boolean isSystemAttribute(String attributeKey) {
        return PricingAttributeKeys.SYSTEM_KEYS.contains(attributeKey);
    }

    @ToAuditableEntity
    @Mapping(target = "dataType", expression = "java(pricingMetadataDto.getDataType() != null ? pricingMetadataDto.getDataType().toUpperCase() : null)")
    PricingInputMetadata toEntity(PricingMetadataRequest pricingMetadataDto);

    @ToAuditableEntity
    void updateFromDto(PricingMetadataRequest dto, @MappingTarget PricingInputMetadata entity);

    @ToAuditableEntity
    PricingInputMetadata clone(PricingInputMetadata source);
}
