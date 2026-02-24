package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductRequest;
import com.bankengine.catalog.dto.ProductResponse;
import com.bankengine.catalog.dto.ProductVersionRequest;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
        ProductTypeMapper typeMapper = Mappers.getMapper(ProductTypeMapper.class);
        FeatureLinkMapper featureMapper = Mappers.getMapper(FeatureLinkMapper.class);
        PricingLinkMapper pricingLinkMapper = Mappers.getMapper(PricingLinkMapper.class);

        ReflectionTestUtils.setField(mapper, "productTypeMapper", typeMapper);
        ReflectionTestUtils.setField(mapper, "featureLinkMapper", featureMapper);
        ReflectionTestUtils.setField(mapper, "pricingLinkMapper", pricingLinkMapper);
    }

    @Test
    @DisplayName("Version Cloning: Should carry over marketing metadata from old product")
    void shouldCorrectlyMapToNewVersion() {
        // Arrange
        ProductType productType = new ProductType();
        productType.setName("Test Product Type");

        Product oldProduct = new Product();
        oldProduct.setId(99L); // Original ID
        oldProduct.setProductType(productType);
        oldProduct.setBankId("BANK-001");
        oldProduct.setCategory("RETAIL");
        oldProduct.setTagline("Original Tagline");
        oldProduct.setFullDescription("Original Description");
        oldProduct.setIconUrl("http://old-icon.png");
        oldProduct.setFeatured(true);

        ProductVersionRequest requestDto = new ProductVersionRequest();
        requestDto.setNewName("Improved Savings V2");
        requestDto.setNewEffectiveDate(LocalDate.now().plusDays(7));

        // Act
        Product newProduct = mapper.createNewVersionFrom(oldProduct, requestDto);

        // Assert
        assertThat(newProduct).isNotNull();
        assertThat(newProduct.getId()).isNull(); // Crucial: ID must be null for new record
        assertThat(newProduct.getName()).isEqualTo("Improved Savings V2");
        assertThat(newProduct.getStatus()).isEqualTo("DRAFT");

        // Inherited Fields
        assertThat(newProduct.getBankId()).isEqualTo("BANK-001");
        assertThat(newProduct.getCategory()).isEqualTo("RETAIL");
        assertThat(newProduct.getTagline()).isEqualTo("Original Tagline");
        assertThat(newProduct.getFullDescription()).isEqualTo("Original Description");
        assertThat(newProduct.getIconUrl()).isEqualTo("http://old-icon.png");
        assertThat(newProduct.isFeatured()).isTrue();
    }

    @Test
    @DisplayName("Defaults: Should set status to DRAFT when request status is null")
    void shouldDefaultToDraftWhenStatusIsNull() {
        // Arrange
        ProductRequest dto = new ProductRequest();
        dto.setName("New Product");
        dto.setStatus(null);
        Product entity = mapper.toEntity(dto, new ProductType());
        assertThat(entity.getStatus()).isEqualTo("DRAFT");
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
    @DisplayName("Update: Should map isFeatured correctly (Boolean naming check)")
    void testUpdateFromDto() {
        ProductRequest dto = new ProductRequest();
        dto.setName("Updated Product");
        dto.setFeatured(true);

        Product entity = new Product();
        entity.setName("Original Product");
        entity.setFeatured(false);

        mapper.updateFromDto(dto, entity);

        assertThat(entity.getName()).isEqualTo("Updated Product");
        assertThat(entity.isFeatured()).isTrue();
    }

    @Test
    @DisplayName("Marketing: Should map all marketing fields from Request DTO")
    void shouldMapMarketingFields_WhenConvertingRequestToEntity() {
        ProductRequest dto = new ProductRequest();
        dto.setName("Global Savings");
        dto.setTagline("Best Savings");
        dto.setFullDescription("Rich text description here");
        dto.setIconUrl("http://image.png");
        dto.setDisplayOrder(5);
        dto.setFeatured(true);
        dto.setTargetCustomerSegments("RETAIL");
        dto.setTermsAndConditions("T&C content");

        ProductType type = new ProductType();
        Product entity = mapper.toEntity(dto, type);

        assertThat(entity.getTagline()).isEqualTo("Best Savings");
        assertThat(entity.getFullDescription()).isEqualTo("Rich text description here");
        assertThat(entity.getIconUrl()).isEqualTo("http://image.png");
        assertThat(entity.getDisplayOrder()).isEqualTo(5);
        assertThat(entity.isFeatured()).isTrue();
        assertThat(entity.getTargetCustomerSegments()).isEqualTo("RETAIL");
        assertThat(entity.getTermsAndConditions()).isEqualTo("T&C content");
    }
}