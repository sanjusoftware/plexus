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
import java.util.List;
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
    @Mock
    private jakarta.persistence.EntityManager entityManager;

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

    @Test
    @DisplayName("Product Lifecycle: Should throw exception when activating a non-DRAFT product")
    void activateProduct_shouldThrowIfAlreadyActive() {
        Product product = new Product();
        product.setStatus("ACTIVE");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        Exception exception = assertThrows(IllegalStateException.class,
                () -> productService.activateProduct(1L, null));

        assertEquals("Only DRAFT products can be directly ACTIVATED.", exception.getMessage());
    }

    @Test
    @DisplayName("Product Lifecycle: Should throw exception when deactivating an already INACTIVE/ARCHIVED product")
    void deactivateProduct_shouldThrowIfAlreadyInactive() {
        Product product = new Product();
        product.setStatus("INACTIVE");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        Exception exception = assertThrows(IllegalStateException.class,
                () -> productService.deactivateProduct(1L));

        assertEquals("Product is already inactive or archived.", exception.getMessage());
    }

    @Test
    @DisplayName("Expiration: Should assert on all validation error messages")
    void extendProductExpiration_shouldValidateInputsWithMessages() {
        Product product = new Product();
        product.setExpirationDate(LocalDate.now().plusDays(10));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // 1. Null date check
        Exception ex1 = assertThrows(IllegalArgumentException.class,
                () -> productService.extendProductExpiration(1L, null));
        assertEquals("New expiration date cannot be null.", ex1.getMessage());

        // 2. Date before current expiration check
        Exception ex2 = assertThrows(IllegalArgumentException.class,
                () -> productService.extendProductExpiration(1L, LocalDate.now().plusDays(5)));
        assertEquals("New expiration date must be after the current expiration date.", ex2.getMessage());

        // 3. Date in the past check
        product.setExpirationDate(null);
        Exception ex3 = assertThrows(IllegalArgumentException.class,
                () -> productService.extendProductExpiration(1L, LocalDate.now().minusDays(1)));
        assertEquals("New expiration date must be in the future.", ex3.getMessage());
    }

    @Test
    @DisplayName("Feature Validation: Should verify data type specific error messages")
    void validateFeatureValue_shouldVerifyErrorMessages() {
        Product product = new Product();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        FeatureComponent comp = new FeatureComponent();
        comp.setId(2L);
        when(featureComponentService.getFeatureComponentById(2L)).thenReturn(comp);

        // INTEGER message check
        comp.setDataType(FeatureComponent.DataType.INTEGER);
        ProductFeature intDto = ProductFeature.builder().featureComponentId(2L).featureValue("ABC").build();
        Exception exInt = assertThrows(IllegalArgumentException.class, () -> productService.linkFeatureToProduct(1L, intDto));
        assertEquals("Feature value 'ABC' must be a valid INTEGER.", exInt.getMessage());

        // BOOLEAN message check
        comp.setDataType(FeatureComponent.DataType.BOOLEAN);
        ProductFeature boolDto = ProductFeature.builder().featureComponentId(2L).featureValue("maybe").build();
        Exception exBool = assertThrows(IllegalArgumentException.class, () -> productService.linkFeatureToProduct(1L, boolDto));
        assertEquals("Feature value 'maybe' must be 'true' or 'false' for BOOLEAN.", exBool.getMessage());
    }

    @Test
    @DisplayName("Sync Features: Should handle deletion of orphans and value updates")
    void syncProductFeatures_shouldHandleUpdatesAndDeletes() {
        Product product = new Product();
        product.setId(1L);
        product.setBankId("TEST_BANK");

        FeatureComponent comp1 = new FeatureComponent();
        comp1.setId(10L);
        comp1.setDataType(FeatureComponent.DataType.STRING);
        FeatureComponent comp2 = new FeatureComponent();
        comp2.setId(20L);
        comp2.setDataType(FeatureComponent.DataType.STRING);

        ProductFeatureLink link1 = new ProductFeatureLink();
        link1.setFeatureComponent(comp1);
        link1.setFeatureValue("OldValue");

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(linkRepository.findByProductId(1L)).thenReturn(java.util.List.of(link1));
        when(featureComponentService.getFeatureComponentById(20L)).thenReturn(comp2);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        // Sync list: remove comp1, add comp2
        ProductFeature dto = ProductFeature.builder()
                .featureComponentId(20L)
                .featureValue("NewValue")
                .build();
        productService.syncProductFeatures(1L, List.of(dto));

        // 1. Verify Deletion of Orphans
        ArgumentCaptor<Iterable<ProductFeatureLink>> deleteCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(linkRepository).deleteAll(deleteCaptor.capture());

        List<ProductFeatureLink> deletedLinks = (List<ProductFeatureLink>) deleteCaptor.getValue();
        assertEquals(1, deletedLinks.size());
        assertEquals(10L, deletedLinks.get(0).getFeatureComponent().getId()); // Ensure comp1 was deleted

        // 2. Verify Creation of New Link
        verify(linkRepository).save(argThat(l -> l.getFeatureValue().equals("NewValue")));
    }

    @Test
    @DisplayName("Version Control: Should only allow versioning of ACTIVE products")
    void createNewVersion_shouldThrowIfProductNotActive() {
        Product product = new Product();
        product.setStatus("DRAFT");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThrows(IllegalStateException.class, () -> productService.createNewVersion(1L, null));
    }

    @Test
    @DisplayName("Sync Features: Should update existing link when feature value changes")
    void syncProductFeatures_shouldUpdateExistingLink() {
        Product product = new Product();
        product.setId(1L);

        FeatureComponent comp = new FeatureComponent();
        comp.setId(50L);
        comp.setDataType(FeatureComponent.DataType.STRING);

        ProductFeatureLink existingLink = new ProductFeatureLink();
        existingLink.setFeatureComponent(comp);
        existingLink.setFeatureValue("OldValue");

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(linkRepository.findByProductId(1L)).thenReturn(List.of(existingLink));
        when(featureComponentService.getFeatureComponentById(50L)).thenReturn(comp);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        // incoming DTO has same ID but NEW value
        ProductFeature dto = ProductFeature.builder()
                .featureComponentId(50L)
                .featureValue("NewValue")
                .build();

        productService.syncProductFeatures(1L, List.of(dto));

        // Verify the existing link was updated and saved
        verify(linkRepository).save(existingLink);
        assertEquals("NewValue", existingLink.getFeatureValue());
    }

    @Test
    @DisplayName("Update Product: Should throw exception for ARCHIVED products")
    void updateProduct_shouldThrowForArchived() {
        Product product = new Product();
        product.setStatus("ARCHIVED");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        Exception ex = assertThrows(IllegalStateException.class,
                () -> productService.updateProduct(1L, new ProductRequest()));
        assertEquals("Cannot update an INACTIVE or ARCHIVED product version.", ex.getMessage());
    }

    @Test
    @DisplayName("Validation: Should throw exception for empty value on non-string type")
    void validateFeatureValue_shouldThrowForEmptyNonString() {
        Product product = new Product();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        FeatureComponent comp = new FeatureComponent();
        comp.setId(2L);
        comp.setDataType(FeatureComponent.DataType.INTEGER); // Not STRING
        when(featureComponentService.getFeatureComponentById(2L)).thenReturn(comp);

        // Blank value
        ProductFeature dto = ProductFeature.builder().featureComponentId(2L).featureValue(" ").build();

        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> productService.linkFeatureToProduct(1L, dto));
        assertTrue(ex.getMessage().contains("Feature value cannot be empty"));
    }

    @Test
    @DisplayName("Validation: Should throw exception for invalid DECIMAL format")
    void validateFeatureValue_shouldThrowForInvalidDecimal() {
        Product product = new Product();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        FeatureComponent comp = new FeatureComponent();
        comp.setId(3L);
        comp.setDataType(FeatureComponent.DataType.DECIMAL);
        when(featureComponentService.getFeatureComponentById(3L)).thenReturn(comp);

        ProductFeature dto = ProductFeature.builder().featureComponentId(3L).featureValue("invalid-price").build();

        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> productService.linkFeatureToProduct(1L, dto));
        assertTrue(ex.getMessage().contains("must be a valid DECIMAL"));
    }

    @Test
    @DisplayName("Sync Features: Should NOT update if value is identical")
    void syncProductFeatures_noUpdateIfValueSame() {
        Product product = new Product();
        product.setId(1L);
        FeatureComponent comp = new FeatureComponent();
        comp.setId(10L); comp.setDataType(FeatureComponent.DataType.STRING);

        ProductFeatureLink existingLink = new ProductFeatureLink();
        existingLink.setFeatureComponent(comp);
        existingLink.setFeatureValue("SameValue");

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(linkRepository.findByProductId(1L)).thenReturn(List.of(existingLink));
        when(featureComponentService.getFeatureComponentById(10L)).thenReturn(comp);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        ProductFeature dto = ProductFeature.builder().featureComponentId(10L).featureValue("SameValue").build();
        productService.syncProductFeatures(1L, List.of(dto));

        // verify save was NOT called for the update branch
        verify(linkRepository, never()).save(existingLink);
    }

    @Test
    @DisplayName("Update Product: Explicitly test ARCHIVED branch")
    void updateProduct_archivedStatus() {
        Product product = new Product();
        product.setStatus("ARCHIVED");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThrows(IllegalStateException.class, () -> productService.updateProduct(1L, new ProductRequest()));
    }

    @Test
    @DisplayName("Deactivate Product: Explicitly test ARCHIVED branch")
    void deactivateProduct_archivedStatus() {
        Product product = new Product();
        product.setStatus("ARCHIVED");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThrows(IllegalStateException.class, () -> productService.deactivateProduct(1L));
    }

    @Test
    @DisplayName("Validation: Edge cases for formatting and default branch")
    void validateFeatureValue_edgeCases() {
        Product product = new Product();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        FeatureComponent comp = new FeatureComponent();
        comp.setId(2L);
        when(featureComponentService.getFeatureComponentById(2L)).thenReturn(comp);

        // DECIMAL format failure (Line 436/439)
        comp.setDataType(FeatureComponent.DataType.DECIMAL);
        ProductFeature decDto = ProductFeature.builder().featureComponentId(2L).featureValue("not.a.number").build();
        assertThrows(IllegalArgumentException.class, () -> productService.linkFeatureToProduct(1L, decDto));

        // Empty value for Boolean (Line 421)
        comp.setDataType(FeatureComponent.DataType.BOOLEAN);
        ProductFeature emptyBoolDto = ProductFeature.builder().featureComponentId(2L).featureValue("").build();
        assertThrows(IllegalArgumentException.class, () -> productService.linkFeatureToProduct(1L, emptyBoolDto));
    }

    @Test
    @DisplayName("Sync Features: Should bypass update when values are identical")
    void syncProductFeatures_shouldNotSaveIfValueIsSame() {
        Product product = new Product();
        product.setId(1L);
        FeatureComponent comp = new FeatureComponent();
        comp.setId(10L); comp.setDataType(FeatureComponent.DataType.STRING);

        ProductFeatureLink existingLink = new ProductFeatureLink();
        existingLink.setFeatureComponent(comp);
        existingLink.setFeatureValue("NoChange");

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(linkRepository.findByProductId(1L)).thenReturn(List.of(existingLink));
        when(featureComponentService.getFeatureComponentById(10L)).thenReturn(comp);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        ProductFeature dto = ProductFeature.builder().featureComponentId(10L).featureValue("NoChange").build();
        productService.syncProductFeatures(1L, List.of(dto));

        // verify save was NEVER called because values matched
        verify(linkRepository, never()).save(existingLink);
    }

    @Test
    @DisplayName("Deactivate Guard: Should throw exception for already ARCHIVED product")
    void deactivateProduct_shouldThrowForArchived() {
        Product product = new Product();
        product.setStatus("ARCHIVED");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        Exception ex = assertThrows(IllegalStateException.class, () -> productService.deactivateProduct(1L));
        assertEquals("Product is already inactive or archived.", ex.getMessage());
    }

    @Test
    @DisplayName("Validation: Handle null check for non-string types")
    void validateFeatureValue_shouldHandleNullForNonString() {
        Product product = new Product();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        FeatureComponent comp = new FeatureComponent();
        comp.setId(5L);
        comp.setDataType(FeatureComponent.DataType.BOOLEAN);
        when(featureComponentService.getFeatureComponentById(5L)).thenReturn(comp);

        ProductFeature dto = ProductFeature.builder().featureComponentId(5L).featureValue(null).build();

        Exception ex = assertThrows(IllegalArgumentException.class, () -> productService.linkFeatureToProduct(1L, dto));
        assertTrue(ex.getMessage().contains("Feature value cannot be empty"));
    }
}