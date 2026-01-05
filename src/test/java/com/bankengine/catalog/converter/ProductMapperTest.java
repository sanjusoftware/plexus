package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.CreateNewVersionRequestDto;
import com.bankengine.catalog.dto.ProductRequest;
import com.bankengine.catalog.dto.ProductResponseDto;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
public class ProductMapperTest {

    @Autowired
    private ProductMapper mapper;

    @Test
    void shouldCorrectlyMapToNewVersion() {
        // Arrange
        ProductType productType = new ProductType();
        productType.setName("Test Product Type");

        Product oldProduct = new Product();
        oldProduct.setProductType(productType);
        oldProduct.setBankId("BANK-001");

        CreateNewVersionRequestDto requestDto = new CreateNewVersionRequestDto();
        requestDto.setNewName("New Product Name");
        requestDto.setNewEffectiveDate(LocalDate.now().plusDays(1));

        // Act
        Product newProduct = mapper.createNewVersionFrom(oldProduct, requestDto);

        // Assert
        assertThat(newProduct).isNotNull();
        assertThat(newProduct.getId()).isNull(); // Should not copy ID
        assertThat(newProduct.getName()).isEqualTo("New Product Name");
        assertThat(newProduct.getBankId()).isEqualTo("BANK-001");
        assertThat(newProduct.getProductType().getName()).isEqualTo("Test Product Type");
        assertThat(newProduct.getEffectiveDate()).isEqualTo(requestDto.getNewEffectiveDate());
        assertThat(newProduct.getStatus()).isEqualTo("DRAFT");
        assertThat(newProduct.getExpirationDate()).isNull();
    }

    @Test
    void testToResponseDto() {
        Product entity = new Product();
        entity.setId(1L);
        entity.setName("Test Product");
        entity.setBankId("BANK-001");

        ProductResponseDto dto = mapper.toResponseDto(entity);

        assertNotNull(dto);
        assertEquals(entity.getId(), dto.getId());
        assertEquals(entity.getName(), dto.getName());
        assertEquals(entity.getBankId(), dto.getBankId());
    }

    @Test
    void testToResponseDtoList() {
        Product entity = new Product();
        entity.setId(1L);
        entity.setName("Test Product");
        entity.setBankId("BANK-001");

        List<ProductResponseDto> dtoList = mapper.toResponseDtoList(Collections.singletonList(entity));

        assertNotNull(dtoList);
        assertEquals(1, dtoList.size());
        assertEquals(entity.getId(), dtoList.get(0).getId());
        assertEquals(entity.getName(), dtoList.get(0).getName());
    }

    @Test
    void testUpdateFromDto() {
        ProductRequest dto = new ProductRequest();
        dto.setName("Updated Product");

        Product entity = new Product();
        entity.setName("Original Product");

        mapper.updateFromDto(dto, entity);

        assertEquals(dto.getName(), entity.getName());
    }
}
