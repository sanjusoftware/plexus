package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.FeatureComponentRequest;
import com.bankengine.catalog.dto.FeatureComponentResponse;
import com.bankengine.catalog.model.FeatureComponent;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FeatureComponentMapperTest {

    private final FeatureComponentMapper mapper = Mappers.getMapper(FeatureComponentMapper.class);

    @Test
    void testToEntity() {
        FeatureComponentRequest dto = new FeatureComponentRequest();
        dto.setName("Test Feature");
        dto.setDataType("STRING");

        FeatureComponent entity = mapper.toEntity(dto);

        assertNotNull(entity);
        assertEquals(dto.getName(), entity.getName());
        assertEquals(FeatureComponent.DataType.STRING, entity.getDataType());
    }

    @Test
    void testToResponseDto() {
        FeatureComponent entity = new FeatureComponent();
        entity.setId(1L);
        entity.setName("Test Feature");

        FeatureComponentResponse dto = mapper.toResponseDto(entity);

        assertNotNull(dto);
        assertEquals(entity.getId(), dto.getId());
        assertEquals(entity.getName(), dto.getName());
    }

    @Test
    void testUpdateFromDto() {
        FeatureComponentRequest dto = new FeatureComponentRequest();
        dto.setName("Updated Feature");
        dto.setDataType("BOOLEAN");

        FeatureComponent entity = new FeatureComponent();
        entity.setName("Original Feature");
        entity.setDataType(FeatureComponent.DataType.STRING);

        mapper.updateFromDto(dto, entity);

        assertEquals(dto.getName(), entity.getName());
        assertEquals(FeatureComponent.DataType.BOOLEAN, entity.getDataType());
    }
}
