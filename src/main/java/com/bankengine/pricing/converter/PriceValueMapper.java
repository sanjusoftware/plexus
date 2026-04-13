package com.bankengine.pricing.converter;

import com.bankengine.common.mapping.ToAuditableEntity;
import com.bankengine.config.MapStructConfig;
import com.bankengine.pricing.dto.PriceValueRequest;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.model.PriceValue;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class, componentModel = "spring")
public interface PriceValueMapper {

    @ToAuditableEntity
    @Mapping(target = "pricingTier", ignore = true)
    @Mapping(target = "matchedTierCode", ignore = true)
    PriceValue clone(PriceValue source);

    @ToAuditableEntity
    @Mapping(target = "pricingTier", ignore = true)
    @Mapping(target = "matchedTierId", ignore = true)
    @Mapping(target = "matchedTierCode", ignore = true)
    @Mapping(target = "componentCode", ignore = true)
    @Mapping(target = "rawValue", source = "priceAmount")
    PriceValue toEntity(PriceValueRequest dto);

    @ToAuditableEntity
    @Mapping(target = "pricingTier", ignore = true)
    @Mapping(target = "matchedTierId", ignore = true)
    @Mapping(target = "matchedTierCode", ignore = true)
    @Mapping(target = "componentCode", ignore = true)
    @Mapping(target = "rawValue", source = "priceAmount")
    void updateFromDto(PriceValueRequest dto, @MappingTarget PriceValue entity);

    @Mapping(target = "componentCode", source = "pricingTier.pricingComponent.code")
    @Mapping(target = "matchedTierId", source = "pricingTier.id")
    @Mapping(target = "matchedTierCode", source = "pricingTier.code")
    @Mapping(target = "sourceType", constant = "CATALOG")
    @Mapping(target = "valueType", source = "valueType") // ADDED: Explicitly map the enum
    @Mapping(target = "calculatedAmount", ignore = true)
    @Mapping(target = "targetComponentCode", ignore = true)
    @Mapping(target = "proRataApplicable", source = "pricingTier.pricingComponent.proRataApplicable")
    @Mapping(target = "applyChargeOnFullBreach", source = "pricingTier.applyChargeOnFullBreach")
    @Mapping(target = "effectiveDate", ignore = true)
    @Mapping(target = "expiryDate", ignore = true)
    @Mapping(target = "activeDays", ignore = true)
    @Mapping(target = "billingCycleDays", ignore = true)
    ProductPricingCalculationResult.PriceComponentDetail toDetailDto(PriceValue entity);
}