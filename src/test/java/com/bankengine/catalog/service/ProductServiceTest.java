package com.bankengine.catalog.service;

import com.bankengine.catalog.converter.FeatureLinkMapper;
import com.bankengine.catalog.converter.PricingLinkMapper;
import com.bankengine.catalog.converter.ProductMapper;
import com.bankengine.catalog.dto.*;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.ProductPricingLink;
import com.bankengine.pricing.service.PricingComponentService;
import com.bankengine.test.config.BaseServiceTest;
import com.bankengine.web.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest extends BaseServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductTypeRepository productTypeRepository;
    @Mock private FeatureComponentService featureComponentService;
    @Mock private PricingComponentService pricingComponentService;
    @Mock private ProductMapper productMapper;
    @Mock private FeatureLinkMapper featureLinkMapper;
    @Mock private PricingLinkMapper pricingLinkMapper;
    @Mock private EntityManager entityManager;

    @InjectMocks
    private ProductService productService;

    // --- HELPERS ---

    private Product createValidProduct(VersionableEntity.EntityStatus status) {
        Product product = new Product();
        product.setId(1L);
        product.setBankId(TEST_BANK_ID);
        product.setCode("PROD-001");
        product.setVersion(1);
        product.setStatus(status);
        product.setProductFeatureLinks(new ArrayList<>());
        product.setProductPricingLinks(new ArrayList<>());
        return product;
    }

    private ProductType createValidProductType() {
        ProductType type = new ProductType();
        type.setId(1L);
        type.setBankId(TEST_BANK_ID); // CRITICAL: Must match for getByIdSecurely
        return type;
    }

    private ProductRequest createFeatureRequest(Long componentId, String value) {
        ProductRequest req = new ProductRequest();
        req.setFeatures(List.of(ProductFeatureDto.builder()
                .featureComponentId(componentId)
                .featureValue(value)
                .build()));
        return req;
    }

    // --- READ OPERATIONS ---

    @Test
    @DisplayName("Search: Should return paged results based on criteria")
    void testSearchProducts() {
        ProductSearchRequest criteria = new ProductSearchRequest();
        criteria.setSortBy("name");
        criteria.setSortDirection("ASC");

        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(createValidProduct(VersionableEntity.EntityStatus.ACTIVE))));
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        Page<ProductResponse> result = productService.searchProducts(criteria);

        assertFalse(result.isEmpty());
        verify(productRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Read: Should throw NotFoundException for non-existent ID")
    void testGetProductResponseById_notFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class, () -> productService.getProductResponseById(99L));
        assertEquals("Product not found with ID: 99", ex.getMessage());
    }

    // --- WRITE & LIFECYCLE ---

    @Test
    @DisplayName("Create: Should initialize product as Version 1 in DRAFT status")
    void testCreateProduct_initialization() {
        ProductRequest dto = new ProductRequest();
        dto.setProductTypeId(1L);
        dto.setName("New Product");

        Product product = createValidProduct(null);

        when(productRepository.existsByNameAndBankId(any(), any())).thenReturn(false);
        when(productRepository.existsByBankIdAndCodeAndVersion(any(), any(), anyInt())).thenReturn(false);
        when(productTypeRepository.findById(1L)).thenReturn(Optional.of(createValidProductType()));

        when(productMapper.toEntity(any(), any())).thenReturn(product);
        when(productRepository.save(any())).thenReturn(product);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        productService.createProduct(dto);

        verify(productRepository).save(argThat(p ->
            p.getStatus() == VersionableEntity.EntityStatus.DRAFT && p.getVersion() == 1
        ));
    }

    @Test
    @DisplayName("Create: Should reject duplicate names and codes")
    void testCreateProduct_UniquenessChecks() {
        ProductRequest dto = new ProductRequest();
        dto.setName("Duplicate Name");
        dto.setCode("EXISTING");

        // Case 1: Name exists
        when(productRepository.existsByNameAndBankId("Duplicate Name", TEST_BANK_ID)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> productService.createProduct(dto));

        // Case 2: Code version 1 exists
        when(productRepository.existsByNameAndBankId(any(), any())).thenReturn(false);
        when(productRepository.existsByBankIdAndCodeAndVersion(TEST_BANK_ID, "EXISTING", 1)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> productService.createProduct(dto));
    }

    @Test
    @DisplayName("Update: Should reject expiration dates in the past or before current")
    void testUpdateProduct_InvalidExpiry() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.DRAFT);
        product.setExpiryDate(LocalDate.now().plusDays(10));

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // Date is valid relative to NOW, but invalid relative to CURRENT EXPIRY (e.g., today + 5)
        ProductRequest reqBeforeCurrent = ProductRequest.builder()
                .expiryDate(LocalDate.now().plusDays(5))
                .build();

        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
                () -> productService.updateProduct(1L, reqBeforeCurrent));
        assertEquals("New expiration date cannot be before current expiration date.", ex1.getMessage());

        // Date is in the past (e.g., yesterday)
        product.setExpiryDate(null);

        ProductRequest reqPast = ProductRequest.builder()
                .expiryDate(LocalDate.now().minusDays(1))
                .build();

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                () -> productService.updateProduct(1L, reqPast));
        assertEquals("Expiration date must be in the future.", ex2.getMessage());
    }

    // --- UPDATED & CONSOLIDATED VERSIONING ---

    @Test
    @DisplayName("Clone: Revision (Same Code) should increment version, inherit ACTIVE status, and archive source")
    void testCloneProduct_Revision_Success() {
        // Arrange
        Product source = createValidProduct(VersionableEntity.EntityStatus.ACTIVE);
        source.setCode("PROD-001");
        source.setVersion(1);

        Product clone = createValidProduct(null);
        LocalDate futureDate = LocalDate.now().plusDays(10);
        VersionRequest request = new VersionRequest("New Version", null, futureDate);

        when(productRepository.findById(1L)).thenReturn(Optional.of(source));
        when(productRepository.existsByBankIdAndCodeAndVersion(any(), any(), anyInt())).thenReturn(false);
        when(productMapper.createNewVersionFrom(any(), any())).thenReturn(clone);
        when(productRepository.save(any())).thenReturn(clone);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        // Act
        productService.cloneProduct(1L, request);

        // Assert
        assertEquals(VersionableEntity.EntityStatus.ACTIVE, clone.getStatus());
        assertEquals(2, clone.getVersion());
        assertEquals(VersionableEntity.EntityStatus.ARCHIVED, source.getStatus());
        assertEquals(futureDate, clone.getActivationDate()); // Replaces testHandleTemporalVersioning

        verify(entityManager).refresh(clone);
        verify(productRepository).flush();
    }

    @Test
    @DisplayName("Clone: Branching (New Code) should reset version to 1 and default to DRAFT")
    void testCloneProduct_Branching_Success() {
        Product source = createValidProduct(VersionableEntity.EntityStatus.ACTIVE);
        source.setCode("OLD-CODE");

        Product branch = createValidProduct(null);
        VersionRequest request = new VersionRequest("New Branch", "NEW-CODE", null);

        when(productRepository.findById(1L)).thenReturn(Optional.of(source));
        when(productRepository.existsByBankIdAndCodeAndVersion(any(), any(), anyInt())).thenReturn(false);
        when(productMapper.createNewVersionFrom(any(), any())).thenReturn(branch);
        when(productRepository.save(any())).thenReturn(branch);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        productService.cloneProduct(1L, request);

        assertEquals("NEW-CODE", branch.getCode());
        assertEquals(1, branch.getVersion());
        assertEquals(VersionableEntity.EntityStatus.DRAFT, branch.getStatus());
        assertEquals(VersionableEntity.EntityStatus.ACTIVE, source.getStatus());
    }

    @Test
    @DisplayName("Clone: Should throw IllegalStateException if version collision occurs")
    void testCloneProduct_VersionCollision() {
        Product source = createValidProduct(VersionableEntity.EntityStatus.ACTIVE);
        source.setCode("COLLIDE");
        source.setVersion(1);

        when(productRepository.findById(1L)).thenReturn(Optional.of(source));
        // Mock that version 2 already exists
        when(productRepository.existsByBankIdAndCodeAndVersion(TEST_BANK_ID, "COLLIDE", 2)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> productService.cloneProduct(1L, new VersionRequest()));
    }

    @Test
    @DisplayName("Clone: Should throw AccessDeniedException if bankId mismatches")
    void testCloneProduct_securityBreach() {
        Product source = createValidProduct(VersionableEntity.EntityStatus.ACTIVE);
        source.setBankId("ATTACKER_BANK");

        when(productRepository.findById(1L)).thenReturn(Optional.of(source));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> productService.cloneProduct(1L, new VersionRequest()));
        assertEquals("You do not have permission to access this Product", ex.getMessage());
    }

    @Test
    @DisplayName("Activate: Should transition DRAFT to ACTIVE and adjust past dates")
    void testActivateProduct_adjustsPastDate() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.DRAFT);
        product.setActivationDate(LocalDate.now().minusMonths(1));

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenReturn(product);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        productService.activateProduct(1L, null);

        assertEquals(VersionableEntity.EntityStatus.ACTIVE, product.getStatus());
        assertEquals(LocalDate.now(), product.getActivationDate());
    }

    @Test
    @DisplayName("Lifecycle Guards: Should prevent invalid status transitions")
    void testLifecycleGuards() {
        Product active = createValidProduct(VersionableEntity.EntityStatus.ACTIVE);
        when(productRepository.findById(1L)).thenReturn(Optional.of(active));

        IllegalStateException ex1 = assertThrows(IllegalStateException.class, () -> productService.updateProduct(1L, new ProductRequest()));
        assertEquals("Operation allowed only on DRAFT status.", ex1.getMessage());

        Product inactive = createValidProduct(VersionableEntity.EntityStatus.INACTIVE);
        when(productRepository.findById(2L)).thenReturn(Optional.of(inactive));

        IllegalStateException ex2 = assertThrows(IllegalStateException.class, () -> productService.deactivateProduct(2L));
        assertEquals("Product is already inactive.", ex2.getMessage());
    }

    // --- INTERNAL RECONCILIATION ---

    @Test
    @DisplayName("Sync Features: Should remove orphaned links")
    void testSyncFeatures_orphanedLinks() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.DRAFT);
        FeatureComponent comp1 = FeatureComponent.builder().id(10L).dataType(FeatureComponent.DataType.STRING).build();
        product.getProductFeatureLinks().add(ProductFeatureLink.builder().featureComponent(comp1).build());

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenReturn(product);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        // Update with an empty list of features
        productService.updateProduct(1L, ProductRequest.builder().features(new ArrayList<>()).build());

        assertTrue(product.getProductFeatureLinks().isEmpty(), "Feature links should be cleared if not in request");
    }

    @Test
    @DisplayName("Validation: Should reject feature values that mismatch DataType")
    void testFeatureDataTypeValidation() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.DRAFT);
        FeatureComponent comp = new FeatureComponent();
        comp.setBankId(TEST_BANK_ID);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(featureComponentService.getFeatureComponentById(anyLong())).thenReturn(comp);

        comp.setDataType(FeatureComponent.DataType.INTEGER);
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
            productService.updateProduct(1L, createFeatureRequest(1L, "abc")));
        assertEquals("Value 'abc' must be an INTEGER.", ex1.getMessage());

        comp.setDataType(FeatureComponent.DataType.BOOLEAN);
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
            productService.updateProduct(1L, createFeatureRequest(1L, "not-bool")));
        assertEquals("Value 'not-bool' must be 'true' or 'false'.", ex2.getMessage());

        comp.setDataType(FeatureComponent.DataType.DECIMAL);
        IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class, () ->
            productService.updateProduct(1L, createFeatureRequest(1L, "12.xx")));
        assertEquals("Value '12.xx' must be a DECIMAL.", ex3.getMessage());
    }

    @Test
    @DisplayName("Sync Features: Should update value if changed, bypass if identical")
    void testSyncFeatures_smartUpdate() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.DRAFT);
        FeatureComponent comp = FeatureComponent.builder().id(10L).dataType(FeatureComponent.DataType.STRING).build();

        ProductFeatureLink existingLink = spy(ProductFeatureLink.builder()
                .featureComponent(comp).featureValue("Original").build());
        product.getProductFeatureLinks().add(existingLink);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(featureComponentService.getFeatureComponentById(10L)).thenReturn(comp);
        when(productRepository.save(any())).thenReturn(product);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        productService.updateProduct(1L, createFeatureRequest(10L, "Original"));
        verify(existingLink, never()).setFeatureValue(anyString());

        productService.updateProduct(1L, createFeatureRequest(10L, "NewValue"));
        assertEquals("NewValue", existingLink.getFeatureValue());
    }

    @Test
    @DisplayName("Sync Pricing: Should map all pricing fields correctly")
    void testSyncPricing_mapping() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.DRAFT);
        PricingComponent pc = new PricingComponent();
        pc.setBankId(TEST_BANK_ID);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(pricingComponentService.getPricingComponentById(anyLong())).thenReturn(pc);
        when(productRepository.save(any())).thenReturn(product);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        ProductPricingDto dto = new ProductPricingDto();
        dto.setPricingComponentId(50L);
        dto.setFixedValue(new java.math.BigDecimal("99.99"));
        dto.setUseRulesEngine(true);

        ProductRequest req = new ProductRequest();
        req.setPricing(List.of(dto));

        productService.updateProduct(1L, req);

        ProductPricingLink saved = product.getProductPricingLinks().getFirst();
        assertAll("Verify pricing link fields integrity",
                () -> assertEquals(new java.math.BigDecimal("99.99"), saved.getFixedValue()),
                () -> assertTrue(saved.isUseRulesEngine()),
                () -> assertEquals(TEST_BANK_ID, saved.getBankId())
        );
    }

    @Test
    @DisplayName("Branch: deactivateProduct success")
    void testDeactivateProduct_success() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.ACTIVE);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenReturn(product);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        productService.deactivateProduct(1L);

        assertEquals(VersionableEntity.EntityStatus.INACTIVE, product.getStatus());
        assertEquals(LocalDate.now(), product.getExpiryDate());
    }

    @Test
    @DisplayName("Branch: extendProductExpiry success")
    void testExtendProductExpiry_success() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.ACTIVE);
        product.setExpiryDate(LocalDate.now().plusDays(10));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenReturn(product);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        LocalDate newExpiry = LocalDate.now().plusDays(20);
        productService.extendProductExpiry(1L, newExpiry);

        assertEquals(newExpiry, product.getExpiryDate());
    }

    @Test
    @DisplayName("Branch: extendProductExpiry failure - past status")
    void testExtendProductExpiry_invalidStatus() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.ARCHIVED);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThrows(IllegalStateException.class, () -> productService.extendProductExpiry(1L, LocalDate.now().plusDays(20)));
    }

    @Test
    @DisplayName("Branch: extendProductExpiry failure - invalid date")
    void testExtendProductExpiry_invalidDate() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.ACTIVE);
        product.setExpiryDate(LocalDate.now().plusDays(10));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThrows(IllegalArgumentException.class, () -> productService.extendProductExpiry(1L, LocalDate.now().plusDays(5)));
    }

    @Test
    @DisplayName("Branch: extendProductExpiry failure - no current expiry, new date in past")
    void testExtendProductExpiry_noCurrent_pastDate() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.ACTIVE);
        product.setExpiryDate(null);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThrows(IllegalArgumentException.class, () -> productService.extendProductExpiry(1L, LocalDate.now().minusDays(1)));
    }

    @Test
    @DisplayName("Branch: validateFeatureValue missing value for non-string")
    void testValidateFeatureValue_missingValue() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.DRAFT);
        FeatureComponent comp = new FeatureComponent();
        comp.setBankId(TEST_BANK_ID);
        comp.setDataType(FeatureComponent.DataType.INTEGER);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(featureComponentService.getFeatureComponentById(anyLong())).thenReturn(comp);

        assertThrows(IllegalArgumentException.class, () -> productService.updateProduct(1L, createFeatureRequest(1L, "")));
    }

    @Test
    @DisplayName("Branch: syncPricingInternal smart update")
    void testSyncPricingInternal_smartUpdate() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.DRAFT);
        PricingComponent pc = new PricingComponent();
        pc.setId(50L);
        pc.setBankId(TEST_BANK_ID);

        ProductPricingLink existingLink = spy(ProductPricingLink.builder()
                .pricingComponent(pc).fixedValue(new java.math.BigDecimal("10.00")).build());
        product.getProductPricingLinks().add(existingLink);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenReturn(product);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        ProductPricingDto dto = new ProductPricingDto();
        dto.setPricingComponentId(50L);
        dto.setFixedValue(new java.math.BigDecimal("10.00"));

        productService.updateProduct(1L, ProductRequest.builder().pricing(List.of(dto)).build());
        verify(existingLink, never()).setFixedValue(any());
    }

    @Test
    @DisplayName("Branch: mapPricingFields date validations")
    void testMapPricingFields_dates() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.DRAFT);
        PricingComponent pc = new PricingComponent();
        pc.setId(50L);
        pc.setBankId(TEST_BANK_ID);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(pricingComponentService.getPricingComponentById(50L)).thenReturn(pc);

        ProductPricingDto dto = new ProductPricingDto();
        dto.setPricingComponentId(50L);
        dto.setEffectiveDate(LocalDate.now().minusDays(1)); // Past effective date

        assertThrows(IllegalArgumentException.class, () -> productService.updateProduct(1L, ProductRequest.builder().pricing(List.of(dto)).build()));

        dto.setEffectiveDate(LocalDate.now().plusDays(10));
        dto.setExpiryDate(LocalDate.now().plusDays(5)); // Expiry before effective
        assertThrows(IllegalArgumentException.class, () -> productService.updateProduct(1L, ProductRequest.builder().pricing(List.of(dto)).build()));
    }

    @Test
    @DisplayName("Branch: createProduct with productTypeCode")
    void testCreateProduct_productTypeCode() {
        ProductRequest dto = new ProductRequest();
        dto.setProductTypeCode("CARD");
        dto.setName("New Product");

        Product product = createValidProduct(null);
        ProductType type = createValidProductType();
        type.setCode("CARD");

        when(productRepository.existsByNameAndBankId(any(), any())).thenReturn(false);
        when(productRepository.existsByBankIdAndCodeAndVersion(any(), any(), anyInt())).thenReturn(false);
        when(productTypeRepository.findByBankIdAndCode(any(), eq("CARD"))).thenReturn(Optional.of(type));

        when(productMapper.toEntity(any(), any())).thenReturn(product);
        when(productRepository.save(any())).thenReturn(product);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        productService.createProduct(dto);

        verify(productTypeRepository).findByBankIdAndCode(any(), eq("CARD"));
    }

    @Test
    @DisplayName("Branch: updateProduct with name and category")
    void testUpdateProduct_nameAndCategory() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.DRAFT);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenReturn(product);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        ProductRequest req = ProductRequest.builder()
                .name("Updated Name")
                .category("WEALTH")
                .build();

        productService.updateProduct(1L, req);

        assertEquals("Updated Name", product.getName());
        assertEquals("WEALTH", product.getCategory());
    }

    @Test
    @DisplayName("Branch: activateProduct with custom date")
    void testActivateProduct_customDate() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.DRAFT);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenReturn(product);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        LocalDate customDate = LocalDate.now().plusDays(5);
        productService.activateProduct(1L, customDate);

        assertEquals(customDate, product.getActivationDate());
    }
}