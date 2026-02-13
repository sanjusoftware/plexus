package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductRequest;
import com.bankengine.catalog.dto.ProductResponse;
import com.bankengine.catalog.dto.ProductVersionRequest;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ProductMapperTest {

    private final ProductMapper mapper = Mappers.getMapper(ProductMapper.class);

    @BeforeEach
    void setUp() {
        // 1. Manually instantiate the sub-mappers
        ProductTypeMapper typeMapper = Mappers.getMapper(ProductTypeMapper.class);
        FeatureLinkMapper featureMapper = Mappers.getMapper(FeatureLinkMapper.class);
        PricingLinkMapper pricingLinkMapper = Mappers.getMapper(PricingLinkMapper.class);

        // 2. Inject them into the main mapper
        ReflectionTestUtils.setField(mapper, "productTypeMapper", typeMapper);
        ReflectionTestUtils.setField(mapper, "featureLinkMapper", featureMapper);
        ReflectionTestUtils.setField(mapper, "pricingLinkMapper", pricingLinkMapper);
    }

    @Test
    void shouldCorrectlyMapToNewVersion() {
        // Arrange
        ProductType productType = new ProductType();
        productType.setName("Test Product Type");

        Product oldProduct = new Product();
        oldProduct.setProductType(productType);
        oldProduct.setBankId("BANK-001");

        ProductVersionRequest requestDto = new ProductVersionRequest();
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
    void testToResponse() {
        Product entity = new Product();
        entity.setId(1L);
        entity.setName("Test Product");
        entity.setBankId("BANK-001");

        ProductResponse dto = mapper.toResponse(entity);

        assertNotNull(dto);
        assertEquals(entity.getId(), dto.getId());
        assertEquals(entity.getName(), dto.getName());
        assertEquals(entity.getBankId(), dto.getBankId());
    }

    @Test
    void testToResponseList() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Test Product");
        product.setBankId("BANK-001");

        List<ProductResponse> dtoList = mapper.toResponseList(Collections.singletonList(product));

        assertNotNull(dtoList);
        assertEquals(1, dtoList.size());
        assertEquals(product.getId(), dtoList.get(0).getId());
        assertEquals(product.getName(), dtoList.get(0).getName());
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
