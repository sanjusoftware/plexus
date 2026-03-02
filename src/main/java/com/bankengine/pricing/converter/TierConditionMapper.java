package com.bankengine.pricing.converter;

import com.bankengine.common.mapping.ToAuditableEntity;
import com.bankengine.config.MapStructConfig;
import com.bankengine.pricing.dto.TierConditionDto;
import com.bankengine.pricing.model.TierCondition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Set;

@Mapper(config = MapStructConfig.class, componentModel = "spring")
public interface TierConditionMapper {

    @ToAuditableEntity
    @Mapping(target = "pricingTier", ignore = true)
    TierCondition toEntity(TierConditionDto dto);

    @ToAuditableEntity
    @Mapping(target = "pricingTier", ignore = true)
    TierCondition clone(TierCondition source);

    TierConditionDto toDto(TierCondition entity);

    Set<TierCondition> toEntitySet(List<TierConditionDto> dtoList);

    List<TierConditionDto> toDtoList(Set<TierCondition> entitySet);
}