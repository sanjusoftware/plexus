package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.CreateNewVersionRequestDto;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductType;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

public class ProductMapperTest {

    private final ProductMapper mapper = Mappers.getMapper(ProductMapper.class);

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
}
