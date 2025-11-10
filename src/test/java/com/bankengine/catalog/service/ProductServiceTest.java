package com.bankengine.catalog.service;

import com.bankengine.catalog.converter.ProductMapper;
import com.bankengine.catalog.dto.*;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.pricing.service.PricingComponentService;
import com.bankengine.web.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductTypeRepository productTypeRepository;
    @Mock
    private ProductFeatureLinkRepository linkRepository;
    @Mock
    private FeatureComponentService featureComponentService;
    @Mock
    private PricingComponentService pricingComponentService;
    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductService productService;

    @Test
    void testSearchProducts() {
        ProductSearchRequestDto criteria = new ProductSearchRequestDto();
        Page<Product> productPage = new PageImpl<>(Collections.singletonList(new Product()));
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(productPage);
        when(productMapper.toResponseDto(any(Product.class))).thenReturn(new ProductResponseDto());

        Page<ProductResponseDto> result = productService.searchProducts(criteria);

        assertEquals(1, result.getTotalElements());
        verify(productRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void testCreateProduct() {
        CreateProductRequestDto dto = new CreateProductRequestDto();
        dto.setProductTypeId(1L);
        when(productTypeRepository.findById(1L)).thenReturn(Optional.of(new ProductType()));
        when(productRepository.save(any(Product.class))).thenReturn(new Product());
        when(productMapper.toResponseDto(any(Product.class))).thenReturn(new ProductResponseDto());

        ProductResponseDto response = productService.createProduct(dto);

        assertNotNull(response);
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void testGetProductResponseById_notFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> productService.getProductResponseById(1L));
    }

    @Test
    void testUpdateProduct_notDraft() {
        Product product = new Product();
        product.setStatus("ACTIVE");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        assertThrows(IllegalStateException.class, () -> productService.updateProduct(1L, new UpdateProductRequestDto()));
    }

    @Test
    void testActivateProduct() {
        Product product = new Product();
        product.setStatus("DRAFT");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(productMapper.toResponseDto(any(Product.class))).thenReturn(new ProductResponseDto());

        productService.activateProduct(1L, LocalDate.now());

        assertEquals("ACTIVE", product.getStatus());
    }

    @Test
    void testDeactivateProduct() {
        Product product = new Product();
        product.setStatus("ACTIVE");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(productMapper.toResponseDto(any(Product.class))).thenReturn(new ProductResponseDto());

        productService.deactivateProduct(1L);

        assertEquals("INACTIVE", product.getStatus());
    }

    @Test
    void testLinkFeatureToProduct() {
        ProductFeatureDto dto = new ProductFeatureDto();
        dto.setProductId(1L);
        dto.setFeatureComponentId(1L);
        dto.setFeatureValue("Test");

        Product product = new Product();
        product.setId(1L);
        FeatureComponent component = new FeatureComponent();
        component.setDataType(FeatureComponent.DataType.STRING);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(featureComponentService.getFeatureComponentById(1L)).thenReturn(component);
        when(productMapper.toResponseDto(any(Product.class))).thenReturn(new ProductResponseDto());

        productService.linkFeatureToProduct(dto);

        verify(linkRepository, times(1)).save(any(ProductFeatureLink.class));
    }
}
