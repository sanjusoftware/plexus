package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.CreateFeatureComponentRequestDto;
import com.bankengine.catalog.dto.FeatureComponentResponseDto;
import com.bankengine.catalog.dto.UpdateFeatureComponentRequestDto;
import com.bankengine.catalog.model.FeatureComponent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface FeatureComponentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "dataType", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    FeatureComponent toEntity(CreateFeatureComponentRequestDto dto);

    FeatureComponentResponseDto toResponseDto(FeatureComponent entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "dataType", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromDto(UpdateFeatureComponentRequestDto dto, @MappingTarget FeatureComponent entity);
}
