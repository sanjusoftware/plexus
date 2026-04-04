package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.FeatureComponentRequest;
import com.bankengine.catalog.dto.FeatureComponentResponse;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.common.model.VersionableEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FeatureComponentMapperTest {

    private final FeatureComponentMapper mapper = new FeatureComponentMapperImpl();

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
        FeatureComponent entity = FeatureComponent.builder()
                .name("Test Feature")
                .dataType(FeatureComponent.DataType.STRING)
                .version(2)
                .status(VersionableEntity.EntityStatus.ACTIVE)
                .activationDate(LocalDate.of(2026, 4, 1))
                .expiryDate(LocalDate.of(2027, 4, 1))
                .build();

        FeatureComponentResponse dto = mapper.toResponseDto(entity);

        assertNotNull(dto);
        assertEquals(entity.getId(), dto.getId());
        assertEquals(entity.getName(), dto.getName());
        assertEquals(2, dto.getVersion());
        assertEquals("ACTIVE", dto.getStatus());
        assertEquals(LocalDate.of(2026, 4, 1), dto.getActivationDate());
        assertEquals(LocalDate.of(2027, 4, 1), dto.getExpiryDate());
    }

    @Test
    void testUpdateFromDto() {
        FeatureComponentRequest dto = new FeatureComponentRequest();
        dto.setName("Updated Feature");
        dto.setCode("UPDATED_FEATURE");
        dto.setDataType("BOOLEAN");

        FeatureComponent entity = FeatureComponent.builder().name("Original Feature").code("ORIGINAL_FEATURE").dataType(FeatureComponent.DataType.STRING).build();

        mapper.updateFromDto(dto, entity);

        assertEquals(dto.getName(), entity.getName());
        assertEquals(dto.getCode(), entity.getCode());
        assertEquals(FeatureComponent.DataType.BOOLEAN, entity.getDataType());
    }
}
