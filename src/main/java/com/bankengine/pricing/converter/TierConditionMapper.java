package com.bankengine.pricing.converter;

import com.bankengine.config.MapStructConfig;
import com.bankengine.pricing.dto.TierConditionDto;
import com.bankengine.pricing.model.TierCondition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Set;

@Mapper(config = MapStructConfig.class)
public interface TierConditionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "pricingTier", ignore = true)
    @Mapping(target = "bankId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    TierCondition toEntity(TierConditionDto dto);

    TierConditionDto toDto(TierCondition entity);

    Set<TierCondition> toEntitySet(List<TierConditionDto> dtoList);

    List<TierConditionDto> toDtoList(Set<TierCondition> entitySet);
}