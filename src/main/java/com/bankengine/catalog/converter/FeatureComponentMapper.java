package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.FeatureComponentRequest;
import com.bankengine.catalog.dto.FeatureComponentResponseDto;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.config.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class)
public interface FeatureComponentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "dataType", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    FeatureComponent toEntity(FeatureComponentRequest dto);

    FeatureComponentResponseDto toResponseDto(FeatureComponent entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "dataType", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromDto(FeatureComponentRequest dto, @MappingTarget FeatureComponent entity);
}
