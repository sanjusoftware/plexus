package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductTypeRequest;
import com.bankengine.catalog.dto.ProductTypeResponseDto;
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
    void testToResponseDto() {
        ProductType entity = new ProductType();
        entity.setId(1L);
        entity.setName("Test Product Type");

        ProductTypeResponseDto dto = mapper.toResponseDto(entity);

        assertNotNull(dto);
        assertEquals(entity.getId(), dto.getId());
        assertEquals(entity.getName(), dto.getName());
    }

    @Test
    void testToResponseDtoList() {
        ProductType entity = new ProductType();
        entity.setId(1L);
        entity.setName("Test Product Type");

        List<ProductTypeResponseDto> dtoList = mapper.toResponseDtoList(Collections.singletonList(entity));

        assertNotNull(dtoList);
        assertEquals(1, dtoList.size());
        assertEquals(entity.getId(), dtoList.get(0).getId());
        assertEquals(entity.getName(), dtoList.get(0).getName());
    }

    @Test
    void testToEntity() {
        ProductTypeRequest dto = new ProductTypeRequest();
        dto.setName("Test Product Type");

        ProductType entity = mapper.toEntity(dto);

        assertNotNull(entity);
        assertEquals(dto.getName(), entity.getName());
    }
}
