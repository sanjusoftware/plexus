package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.FeatureComponentResponse;
import com.bankengine.catalog.dto.ProductCatalogCard;
import com.bankengine.catalog.dto.ProductTypeDto;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CatalogMappersTest {

    private FeatureComponentMapper featureComponentMapper;
    private ProductTypeMapper productTypeMapper;
    private ProductMapper productMapper;

    @BeforeEach
    void setUp() {
        this.featureComponentMapper = new FeatureComponentMapperImpl();
        this.productTypeMapper = new ProductTypeMapperImpl();
        FeatureLinkMapper featureLinkMapper = new FeatureLinkMapperImpl();
        PricingLinkMapper pricingLinkMapper = new PricingLinkMapperImpl();

        this.productMapper = new ProductMapperImpl(
            productTypeMapper,
            featureLinkMapper,
            pricingLinkMapper
        );
    }

    @Test
    void testFeatureComponentMapper() {
        FeatureComponent entity = FeatureComponent.builder()
                .name("ATM Limit")
                .dataType(FeatureComponent.DataType.INTEGER)
                .build();

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
        ProductType type = new ProductType();
        type.setName("Type X");

        Product product = new Product();
        product.setId(100L);
        product.setName("Product 100");
        product.setProductType(type);

        ProductCatalogCard card = productMapper.toCatalogCard(product);

        assertNotNull(card);
        assertEquals(100L, card.getProductId());
        assertEquals("Product 100", card.getProductName());
        assertEquals("Type X", card.getProductTypeDisplayName());
    }
}