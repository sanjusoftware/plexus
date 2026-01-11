package com.bankengine.pricing.converter;

import com.bankengine.common.mapping.ToAuditableEntity;
import com.bankengine.config.MapStructConfig;
import com.bankengine.pricing.dto.PricingTierRequest;
import com.bankengine.pricing.dto.PricingTierResponse;
import com.bankengine.pricing.model.PricingTier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class, uses = {PriceValueMapper.class, TierConditionMapper.class})
public interface PricingTierMapper {

    PricingTierResponse toResponse(PricingTier entity);

    @ToAuditableEntity
    @Mapping(target = "pricingComponent", ignore = true)
    @Mapping(target = "priceValues", ignore = true)
    @Mapping(target = "conditions", ignore = true)
    PricingTier toEntity(PricingTierRequest pricingTierRequest);

    @ToAuditableEntity
    @Mapping(target = "pricingComponent", ignore = true)
    @Mapping(target = "priceValues", ignore = true)
    @Mapping(target = "conditions", ignore = true)
    void updateFromDto(PricingTierRequest pricingTierRequest, @MappingTarget PricingTier entity);
}