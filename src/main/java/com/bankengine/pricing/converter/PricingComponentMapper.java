package com.bankengine.pricing.converter;

import com.bankengine.config.MapStructConfig;
import com.bankengine.pricing.dto.PricingComponentRequest;
import com.bankengine.pricing.dto.PricingComponentResponseDto;
import com.bankengine.pricing.dto.PricingTierResponseDto;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface PricingComponentMapper {

    @Mapping(target = "componentCode", source = "pricingTier.pricingComponent.name")
    @Mapping(target = "amount", source = "priceAmount")
    @Mapping(target = "context", constant = "CATALOG_DETAIL")
    @Mapping(target = "sourceType", constant = "CATALOG")
    ProductPricingCalculationResult.PriceComponentDetail toPriceValueDto(PriceValue entity);

    @Mapping(target = "priceValues", source = "priceValues")
    PricingTierResponseDto toPricingTierDto(PricingTier entity);

    @Mapping(target = "pricingTiers", source = "pricingTiers")
    PricingComponentResponseDto toResponseDto(PricingComponent entity);

    List<PricingComponentResponseDto> toResponseDtoList(List<PricingComponent> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "pricingTiers", ignore = true)
    PricingComponent toEntity(PricingComponentRequest dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "pricingTiers", ignore = true)
    void updateFromDto(PricingComponentRequest dto, @MappingTarget PricingComponent entity);
}