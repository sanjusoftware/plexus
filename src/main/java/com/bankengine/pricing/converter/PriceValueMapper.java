package com.bankengine.pricing.converter;

import com.bankengine.common.mapping.ToAuditableEntity;
import com.bankengine.config.MapStructConfig;
import com.bankengine.pricing.dto.PriceValueRequest;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.model.PriceValue;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class)
public interface PriceValueMapper {

    @ToAuditableEntity
    @Mapping(target = "pricingTier", ignore = true)
    @Mapping(target = "valueType", ignore = true)
    @Mapping(target = "matchedTierId", ignore = true)
    @Mapping(target = "componentCode", ignore = true)
    PriceValue toEntity(PriceValueRequest dto);

    @ToAuditableEntity
    @Mapping(target = "pricingTier", ignore = true)
    @Mapping(target = "valueType", ignore = true)
    @Mapping(target = "matchedTierId", ignore = true)
    @Mapping(target = "componentCode", ignore = true)
    void updateFromDto(PriceValueRequest dto, @MappingTarget PriceValue entity);

    @Mapping(target = "amount", source = "priceAmount")
    @Mapping(target = "componentCode", source = "pricingTier.pricingComponent.name")
    @Mapping(target = "matchedTierId", source = "pricingTier.id")
    @Mapping(target = "sourceType", constant = "CATALOG")
    ProductPricingCalculationResult.PriceComponentDetail toDetailDto(PriceValue entity);
}