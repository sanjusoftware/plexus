package com.bankengine.pricing.converter;

import com.bankengine.config.MapStructConfig;
import com.bankengine.pricing.dto.CreatePriceValueRequestDto;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.dto.UpdatePriceValueRequestDto;
import com.bankengine.pricing.model.PriceValue;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class)
public interface PriceValueMapper {

    // For addTierAndValue
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "pricingTier", ignore = true) // Set manually in service
    @Mapping(target = "valueType", ignore = true)   // Set manually in service due to UPPERCASE logic
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "matchedTierId", ignore = true)
    @Mapping(target = "componentCode", ignore = true)
    PriceValue toEntity(CreatePriceValueRequestDto dto);

    // For updateTierAndValue (update)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "pricingTier", ignore = true)
    @Mapping(target = "valueType", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "matchedTierId", ignore = true)
    @Mapping(target = "componentCode", ignore = true)
    void updateFromDto(UpdatePriceValueRequestDto dto, @MappingTarget PriceValue entity);

    @Mapping(target = "valueType", source = "valueType")
    @Mapping(source = "pricingTier.pricingComponent.name", target = "componentCode")
    @Mapping(source = "priceAmount", target = "amount")
    @Mapping(target = "context", constant = "PRODUCT_TIER")
    @Mapping(target = "sourceType", constant = "CATALOG")
    @Mapping(target = "matchedTierId", source = "matchedTierId")
    ProductPricingCalculationResult.PriceComponentDetail toResponseDto(PriceValue entity);
}