package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.FeatureComponentResponse;
import com.bankengine.catalog.dto.ProductCatalogCard;
import com.bankengine.catalog.dto.ProductTypeDto;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.test.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CatalogMappersIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private FeatureComponentMapper featureComponentMapper;

    @Autowired
    private ProductTypeMapper productTypeMapper;

    @Autowired
    private ProductMapper productMapper;

    @Test
    void testFeatureComponentMapper() {
        FeatureComponent entity = new FeatureComponent();
        entity.setName("ATM Limit");
        entity.setDataType(FeatureComponent.DataType.INTEGER);

        FeatureComponentResponse response = featureComponentMapper.toResponseDto(entity);
        assertNotNull(response);
        assertEquals("ATM Limit", response.getName());
    }

    @Test
    void testProductTypeMapper() {
        ProductType entity = new ProductType();
        entity.setName("CASA");

        ProductTypeDto response = productTypeMapper.toResponse(entity);
        assertNotNull(response);
        assertEquals("CASA", response.getName());

        ProductType entity2 = productTypeMapper.toEntity(response);
        assertEquals("CASA", entity2.getName());
    }

    @Test
    void testProductMapper() {
        Product product = new Product();
        product.setId(100L);
        product.setName("Product 100");
        product.setProductType(new ProductType());
        product.getProductType().setName("Type X");

        ProductCatalogCard card = productMapper.toCatalogCard(product);
        assertNotNull(card);
        assertEquals(100L, card.getProductId());
        assertEquals("Product 100", card.getProductName());
        assertEquals("Type X", card.getProductTypeDisplayName());
    }
}