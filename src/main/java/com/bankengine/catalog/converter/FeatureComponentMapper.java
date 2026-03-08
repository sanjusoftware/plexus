package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.FeatureComponentRequest;
import com.bankengine.catalog.dto.FeatureComponentResponse;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.FeatureComponent.DataType;
import com.bankengine.common.mapping.ToNewEntity;
import com.bankengine.common.mapping.ToVersionableEntity;
import com.bankengine.config.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.Arrays;
import java.util.List;

@Mapper(config = MapStructConfig.class, componentModel = "spring")
public interface FeatureComponentMapper {

    @ToNewEntity
    @Mapping(target = "dataType", source = "dataType", qualifiedByName = "mapDataType")
    FeatureComponent toEntity(FeatureComponentRequest dto);

    FeatureComponentResponse toResponseDto(FeatureComponent entity);

    List<FeatureComponentResponse> toResponseDtoList(List<FeatureComponent> entities);

    @ToVersionableEntity
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "dataType", source = "dataType", qualifiedByName = "mapDataType")
    void updateFromDto(FeatureComponentRequest dto, @MappingTarget FeatureComponent entity);

    @ToNewEntity
    FeatureComponent clone(FeatureComponent source);

    @Named("mapDataType")
    default DataType mapDataType(String dataType) {
        if (dataType == null) throw new IllegalArgumentException("Data type is required.");
        try {
            return DataType.valueOf(dataType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid data type provided: " + dataType + ". Allowed values are: " + Arrays.toString(DataType.values()));
        }
    }
}