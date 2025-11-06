package com.bankengine.pricing.converter;

import com.bankengine.pricing.dto.TierConditionDto;
import com.bankengine.pricing.model.TierCondition;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// If you were using MapStruct, you would use @Mapper(componentModel = "spring")
// Since you are implementing it manually, @Component is sufficient.
@Component
public class TierConditionMapper {

    /**
     * Converts a TierConditionDto to a TierCondition entity.
     * NOTE: The 'pricingTier' back-reference MUST be set in the service layer (PricingComponentService)
     * after the entity is created.
     */
    public TierCondition toEntity(TierConditionDto dto) {
        if (dto == null) {
            return null;
        }

        TierCondition entity = new TierCondition();
        entity.setAttributeName(dto.getAttributeName());
        entity.setOperator(dto.getOperator());
        entity.setAttributeValue(dto.getAttributeValue());
        entity.setConnector(dto.getConnector());
        // ID and Auditable fields are handled by JPA/Database
        return entity;
    }

    /**
     * Converts a TierCondition entity to a TierConditionDto.
     */
    public TierConditionDto toDto(TierCondition entity) {
        if (entity == null) {
            return null;
        }

        TierConditionDto dto = new TierConditionDto();
        dto.setAttributeName(entity.getAttributeName());
        dto.setOperator(entity.getOperator());
        dto.setAttributeValue(entity.getAttributeValue());
        dto.setConnector(entity.getConnector());
        return dto;
    }

    /**
     * Converts a list of DTOs to a Set of entities.
     */
    public Set<TierCondition> toEntitySet(List<TierConditionDto> dtoList) {
        if (dtoList == null) {
            return new HashSet<>();
        }
        return dtoList.stream()
                .map(this::toEntity)
                .collect(Collectors.toSet());
    }

    /**
     * Converts a Set of entities to a list of DTOs.
     */
    public List<TierConditionDto> toDtoList(Set<TierCondition> entitySet) {
        if (entitySet == null) {
            return List.of();
        }
        return entitySet.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
}