package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.FeatureComponentRequest;
import com.bankengine.catalog.dto.FeatureComponentResponseDto;
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

        FeatureComponent entity = mapper.toEntity(dto);

        assertNotNull(entity);
        assertEquals(dto.getName(), entity.getName());
    }

    @Test
    void testToResponseDto() {
        FeatureComponent entity = new FeatureComponent();
        entity.setId(1L);
        entity.setName("Test Feature");

        FeatureComponentResponseDto dto = mapper.toResponseDto(entity);

        assertNotNull(dto);
        assertEquals(entity.getId(), dto.getId());
        assertEquals(entity.getName(), dto.getName());
    }

    @Test
    void testUpdateFromDto() {
        FeatureComponentRequest dto = new FeatureComponentRequest();
        dto.setName("Updated Feature");

        FeatureComponent entity = new FeatureComponent();
        entity.setName("Original Feature");

        mapper.updateFromDto(dto, entity);

        assertEquals(dto.getName(), entity.getName());
    }
}
