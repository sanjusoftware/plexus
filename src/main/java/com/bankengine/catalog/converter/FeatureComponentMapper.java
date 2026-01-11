package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.FeatureComponentRequest;
import com.bankengine.catalog.dto.FeatureComponentResponse;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.FeatureComponent.DataType;
import com.bankengine.common.mapping.ToAuditableEntity;
import com.bankengine.config.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

@Mapper(config = MapStructConfig.class)
public interface FeatureComponentMapper {

    @ToAuditableEntity
    @Mapping(target = "dataType", source = "dataType", qualifiedByName = "mapDataType")
    FeatureComponent toEntity(FeatureComponentRequest dto);

    FeatureComponentResponse toResponseDto(FeatureComponent entity);

    @ToAuditableEntity
    @Mapping(target = "dataType", source = "dataType", qualifiedByName = "mapDataType")
    void updateFromDto(FeatureComponentRequest dto, @MappingTarget FeatureComponent entity);

    /**
     * Defender method to provide a clean error message for the API.
     */
    @Named("mapDataType")
    default DataType mapDataType(String dataType) {
        if (dataType == null) {
            throw new IllegalArgumentException("Data type is required and cannot be null");
        }
        try {
            return DataType.valueOf(dataType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid data type provided: " + dataType);
        }
    }
}