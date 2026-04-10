package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductTypeDto;
import com.bankengine.catalog.model.ProductType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProductTypeMapperTest {

    private final ProductTypeMapper mapper = new ProductTypeMapperImpl();

    @Test
    void testToResponse() {
        ProductType entity = new ProductType();
        entity.setId(1L);
        entity.setName("Test Product Type");
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 10, 12, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 4, 10, 12, 30);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(updatedAt);

        ProductTypeDto dto = mapper.toResponse(entity);

        assertNotNull(dto);
        assertEquals(entity.getId(), dto.getId());
        assertEquals(entity.getName(), dto.getName());
        assertEquals(createdAt, dto.getCreatedAt());
        assertEquals(updatedAt, dto.getUpdatedAt());
    }

    @Test
    void testToResponseList() {
        ProductType entity = new ProductType();
        entity.setId(1L);
        entity.setName("Test Product Type");

        List<ProductTypeDto> dtoList = mapper.toResponseList(Collections.singletonList(entity));

        assertNotNull(dtoList);
        assertEquals(1, dtoList.size());
        assertEquals(entity.getId(), dtoList.getFirst().getId());
        assertEquals(entity.getName(), dtoList.getFirst().getName());
    }

    @Test
    void testToEntity() {
        ProductTypeDto dto = new ProductTypeDto();
        dto.setName("Test Product Type");

        ProductType entity = mapper.toEntity(dto);

        assertNotNull(entity);
        assertEquals(dto.getName(), entity.getName());
    }
}