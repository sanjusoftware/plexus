package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductTypeDto;
import com.bankengine.catalog.model.ProductType;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ProductTypeMapperTest {

    private final ProductTypeMapper mapper = Mappers.getMapper(ProductTypeMapper.class);

    @Test
    void testToResponse() {
        ProductType entity = new ProductType();
        entity.setId(1L);
        entity.setName("Test Product Type");

        ProductTypeDto dto = mapper.toResponse(entity);

        assertNotNull(dto);
        assertEquals(entity.getId(), dto.getId());
        assertEquals(entity.getName(), dto.getName());
    }

    @Test
    void testToResponseList() {
        ProductType entity = new ProductType();
        entity.setId(1L);
        entity.setName("Test Product Type");

        List<ProductTypeDto> dtoList = mapper.toResponseList(Collections.singletonList(entity));

        assertNotNull(dtoList);
        assertEquals(1, dtoList.size());
        assertEquals(entity.getId(), dtoList.get(0).getId());
        assertEquals(entity.getName(), dtoList.get(0).getName());
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
