package com.bankengine.catalog.service;

import com.bankengine.catalog.converter.ProductMapper;
import com.bankengine.catalog.dto.ProductFeature;
import com.bankengine.catalog.dto.ProductRequest;
import com.bankengine.catalog.dto.ProductResponse;
import com.bankengine.catalog.dto.ProductSearchRequest;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.test.config.BaseServiceTest;
import com.bankengine.web.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
public class ProductServiceTest extends BaseServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductTypeRepository productTypeRepository;
    @Mock
    private ProductFeatureLinkRepository linkRepository;
    @Mock
    private FeatureComponentService featureComponentService;
    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("Search Products - Should return paged responses based on search criteria")
    void testSearchProducts() {
        ProductSearchRequest criteria = new ProductSearchRequest();
        Page<Product> productPage = new PageImpl<>(Collections.singletonList(new Product()));
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(productPage);
        when(productMapper.toResponse(any(Product.class))).thenReturn(new ProductResponse());

        Page<ProductResponse> result = productService.searchProducts(criteria);

        assertEquals(1, result.getTotalElements());
        verify(productRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Create Product - Should initialize product and assign bankId from context")
    void testCreateProduct() {
        ProductRequest dto = new ProductRequest();
        dto.setProductTypeId(1L);
        dto.setName("New Product");

        when(productTypeRepository.findById(1L)).thenReturn(Optional.of(new ProductType()));
        when(productRepository.save(argThat(p -> TEST_BANK_ID.equals(p.getBankId())))).thenReturn(new Product());
        when(productMapper.toResponse(any(Product.class))).thenReturn(new ProductResponse());

        assertNotNull(productService.createProduct(dto));
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("Activate Product - Should transition status from DRAFT to ACTIVE")
    void testActivateProduct() {
        Product product = new Product();
        product.setStatus("DRAFT");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(productMapper.toResponse(any(Product.class))).thenReturn(new ProductResponse());

        productService.activateProduct(1L, LocalDate.now());

        assertEquals("ACTIVE", product.getStatus());
    }

    @Test
    @DisplayName("Update Product - Should throw exception if product is already ACTIVE")
    void testUpdateProduct_notDraft() {
        Product product = new Product();
        product.setStatus("ACTIVE");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        assertThrows(IllegalStateException.class, () -> productService.updateProduct(1L, new ProductRequest()));
    }

    @Test
    void testDeactivateProduct() {
        Product product = new Product();
        product.setStatus("ACTIVE");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(productMapper.toResponse(any(Product.class))).thenReturn(new ProductResponse());

        productService.deactivateProduct(1L);

        assertEquals("INACTIVE", product.getStatus());
    }

    @Test
    @DisplayName("Link Feature - Should validate data type and save link")
    void testLinkFeatureToProduct() {
        // 1. Arrange
        ProductFeature dto = new ProductFeature();
        dto.setFeatureComponentId(1L);
        dto.setFeatureValue("100");

        Product product = new Product();
        product.setId(1L);
//        product.setBankId(TEST_BANK_ID);

        FeatureComponent component = new FeatureComponent();
        component.setId(1L);
        component.setDataType(FeatureComponent.DataType.INTEGER);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(featureComponentService.getFeatureComponentById(1L)).thenReturn(component);
        when(productMapper.toResponse(any(Product.class))).thenReturn(new ProductResponse());

        // 2. Act
        productService.linkFeatureToProduct(1L, dto);

        // 3. Assert - Use ArgumentCaptor for clearer debugging
        ArgumentCaptor<ProductFeatureLink> linkCaptor = ArgumentCaptor.forClass(ProductFeatureLink.class);
        verify(linkRepository).save(linkCaptor.capture());

        ProductFeatureLink savedLink = linkCaptor.getValue();

        assertAll("Verify saved link properties",
                () -> assertEquals(1L, savedLink.getProduct().getId(), "Product ID mismatch"),
                () -> assertEquals("100", savedLink.getFeatureValue(), "Feature value mismatch"),
                () -> assertEquals(component, savedLink.getFeatureComponent(), "Feature component mismatch")
        );
    }

    @Test
    @DisplayName("Get Product - Should throw NotFoundException for missing IDs")
    void testGetProductResponseById_notFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> productService.getProductResponseById(1L));
    }
}