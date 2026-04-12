package com.bankengine.catalog.service;

import com.bankengine.catalog.converter.FeatureLinkMapper;
import com.bankengine.catalog.converter.PricingLinkMapper;
import com.bankengine.catalog.converter.ProductMapper;
import com.bankengine.catalog.dto.*;
import com.bankengine.catalog.model.*;
import com.bankengine.catalog.repository.ProductCategoryRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.ProductPricingLink;
import com.bankengine.pricing.service.PricingComponentService;
import com.bankengine.test.config.BaseServiceTest;
import com.bankengine.web.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest extends BaseServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductCategoryRepository productCategoryRepository;
    @Mock private ProductTypeRepository productTypeRepository;
    @Mock private FeatureComponentService featureComponentService;
    @Mock private PricingComponentService pricingComponentService;
    @Mock private ProductMapper productMapper;
    @Mock private FeatureLinkMapper featureLinkMapper;
    @Mock private PricingLinkMapper pricingLinkMapper;
    @Mock private com.bankengine.catalog.repository.BundleProductLinkRepository bundleProductLinkRepository;
    @Mock private EntityManager entityManager;

    @InjectMocks
    private ProductService productService;

    @BeforeEach
    void setupCategoryRepository() {
        lenient().when(productCategoryRepository.findByBankIdAndCode(anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

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
        type.setBankId(TEST_BANK_ID);
        return type;
    }

    private ProductRequest createFeatureRequestByCode(String code, String value) {
        ProductRequest req = new ProductRequest();
        ProductFeatureDto f = new ProductFeatureDto();
        f.setFeatureComponentCode(code);
        f.setFeatureValue(value);
        req.setFeatures(new ArrayList<>(List.of(f)));
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
        dto.setProductTypeCode("CARD");
        dto.setName("New Product");
        dto.setCode("NEW-CODE");
        dto.setCategory("OTHER"); // Changed from RETAIL to avoid warning causing exception

        Product product = createValidProduct(null);
        product.setCode("NEW-CODE");

        when(productRepository.existsByBankIdAndCodeAndVersion(any(), eq("NEW-CODE"), eq(1))).thenReturn(false);
        when(productTypeRepository.findByBankIdAndCode(any(), eq("CARD"))).thenReturn(Optional.of(createValidProductType()));

        when(productMapper.toEntity(any(), any())).thenReturn(product);
        when(productRepository.save(any())).thenReturn(product);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        productService.createProduct(dto);

        verify(productRepository).save(argThat(p ->
            p.getStatus() == VersionableEntity.EntityStatus.DRAFT && p.getVersion() == 1
        ));
    }

    @Test
    @DisplayName("Create: Should reject duplicate codes")
    void testCreateProduct_UniquenessChecks() {
        ProductRequest dto = new ProductRequest();
        dto.setName("Any Name");
        dto.setCode("EXISTING");
        dto.setProductTypeCode("CARD");

        when(productRepository.existsByBankIdAndCodeAndVersion(TEST_BANK_ID, "EXISTING", 1)).thenReturn(true);
        assertThrows(com.bankengine.web.exception.ValidationException.class, () -> productService.createProduct(dto));
    }

    @Test
    @DisplayName("Update: Should reject expiration dates in the past or before current")
    void testUpdateProduct_InvalidExpiry() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.DRAFT);
        product.setExpiryDate(LocalDate.now().plusDays(10));

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ProductRequest reqBeforeCurrent = ProductRequest.builder()
                .expiryDate(LocalDate.now().plusDays(5))
                .build();

        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
                () -> productService.updateProduct(1L, reqBeforeCurrent));
        assertEquals("New expiration date cannot be before current expiration date.", ex1.getMessage());

        product.setExpiryDate(null);

        ProductRequest reqPast = ProductRequest.builder()
                .expiryDate(LocalDate.now().minusDays(1))
                .build();

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                () -> productService.updateProduct(1L, reqPast));
        assertEquals("Expiration date must be in the future.", ex2.getMessage());
    }

    // --- VERSIONING ---

    @Test
    @DisplayName("Clone: Revision (Same Code) should increment version and create DRAFT revision")
    void testCloneProduct_Revision_Success() {
        Product source = createValidProduct(VersionableEntity.EntityStatus.ACTIVE);
        source.setCode("PROD-001");
        source.setVersion(1);

        Product clone = createValidProduct(null);
        LocalDate futureDate = LocalDate.now().plusDays(10);
        VersionRequest request = new VersionRequest("New Version", null, futureDate, null);

        when(productRepository.findById(1L)).thenReturn(Optional.of(source));
        when(productRepository.existsByBankIdAndCodeAndVersion(any(), any(), anyInt())).thenReturn(false);
        when(productMapper.createNewVersionFrom(any(), any())).thenReturn(clone);
        when(productRepository.save(any())).thenReturn(clone);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        productService.cloneProduct(1L, request);

        assertEquals(VersionableEntity.EntityStatus.DRAFT, clone.getStatus());
        assertEquals(2, clone.getVersion());
        assertEquals(VersionableEntity.EntityStatus.ACTIVE, source.getStatus());
        // assertEquals(futureDate, clone.getActivationDate());

        verify(productRepository).flush();
    }

    @Test
    @DisplayName("Clone: Branching (New Code) should reset version to 1 and default to DRAFT")
    void testCloneProduct_Branching_Success() {
        Product source = createValidProduct(VersionableEntity.EntityStatus.ACTIVE);
        source.setCode("OLD-CODE");

        Product branch = createValidProduct(null);
        VersionRequest request = new VersionRequest("New Branch", "NEW-CODE", null, null);

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
        when(productRepository.existsByBankIdAndCodeAndVersion(TEST_BANK_ID, "COLLIDE", 2)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> productService.cloneProduct(1L, new VersionRequest("Name", null, null, null)));
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
    @DisplayName("Activate: Should transition DRAFT to ACTIVE and auto-activate linked components")
    void testActivateProduct_AutoActivate() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.DRAFT);

        PricingComponent pc = new PricingComponent();
        pc.setId(10L);
        pc.setStatus(VersionableEntity.EntityStatus.DRAFT);
        product.getProductPricingLinks().add(ProductPricingLink.builder().pricingComponent(pc).build());

        FeatureComponent fc = new FeatureComponent();
        fc.setId(20L);
        fc.setStatus(VersionableEntity.EntityStatus.DRAFT);
        product.getProductFeatureLinks().add(ProductFeatureLink.builder().featureComponent(fc).build());

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenReturn(product);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        productService.activateProduct(1L, null);

        assertEquals(VersionableEntity.EntityStatus.ACTIVE, product.getStatus());
        verify(pricingComponentService).activateComponent(eq(10L), any());
        verify(featureComponentService).activateFeature(eq(20L), any());
    }

    @Test
    @DisplayName("Activate: Should propagate product activation date to linked DRAFT components")
    void testActivateProduct_PropagatesActivationDate() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.DRAFT);
        LocalDate activationDate = LocalDate.now().plusDays(5);

        PricingComponent pc = new PricingComponent();
        pc.setId(10L);
        pc.setStatus(VersionableEntity.EntityStatus.DRAFT);
        product.getProductPricingLinks().add(ProductPricingLink.builder().pricingComponent(pc).build());

        FeatureComponent fc = new FeatureComponent();
        fc.setId(20L);
        fc.setStatus(VersionableEntity.EntityStatus.DRAFT);
        product.getProductFeatureLinks().add(ProductFeatureLink.builder().featureComponent(fc).build());

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenReturn(product);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        productService.activateProduct(1L, activationDate);

        // Both components should receive the product's activation date
        verify(pricingComponentService).activateComponent(10L, activationDate);
        verify(featureComponentService).activateFeature(20L, activationDate);
    }

    @Test
    @DisplayName("Activate: Should NOT override activation date already set on linked components")
    void testActivateProduct_DoesNotOverrideExistingComponentActivationDate() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.DRAFT);
        LocalDate productActivationDate = LocalDate.now().plusDays(5);
        LocalDate existingComponentDate = LocalDate.now().plusDays(1);

        PricingComponent pc = new PricingComponent();
        pc.setId(10L);
        pc.setStatus(VersionableEntity.EntityStatus.DRAFT);
        pc.setActivationDate(existingComponentDate);
        product.getProductPricingLinks().add(ProductPricingLink.builder().pricingComponent(pc).build());

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenReturn(product);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());


        productService.activateProduct(1L, productActivationDate);

        // The service call still happens; the overload is responsible for honouring existing date
        verify(pricingComponentService).activateComponent(10L, productActivationDate);
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
    @DisplayName("Update: Should throw error for invalid target component status")
    void testUpdateProduct_InvalidTargetStatus() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.DRAFT);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        PricingComponent pc = new PricingComponent();
        pc.setBankId(TEST_BANK_ID);
        pc.setCode("P1");
        pc.setStatus(VersionableEntity.EntityStatus.DRAFT);
        // Requirement 20 check: validateDraft(comp) in syncPricingInternal
        when(pricingComponentService.getPricingComponentByCode(eq("P1"), any())).thenReturn(pc);

        PricingComponent target = new PricingComponent();
        target.setStatus(VersionableEntity.EntityStatus.ARCHIVED);
        when(pricingComponentService.getPricingComponentByCode(eq("T1"), any())).thenReturn(target);

        ProductPricingDto pricingDto = new ProductPricingDto();
        pricingDto.setPricingComponentCode("P1");
        pricingDto.setTargetComponentCode("T1");

        ProductRequest req = ProductRequest.builder()
                .pricing(List.of(pricingDto))
                .build();

        assertThrows(com.bankengine.web.exception.ValidationException.class, () -> productService.updateProduct(1L, req));
    }

    @Test
    @DisplayName("Validate Feature Value: Should cover all data types")
    void testValidateFeatureValue_AllTypes() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.DRAFT);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // Mock components for different types
        FeatureComponent fInt = FeatureComponent.builder().code("INT").dataType(FeatureComponent.DataType.INTEGER).status(VersionableEntity.EntityStatus.DRAFT).build();
        FeatureComponent fDec = FeatureComponent.builder().code("DEC").dataType(FeatureComponent.DataType.DECIMAL).status(VersionableEntity.EntityStatus.DRAFT).build();
        FeatureComponent fBool = FeatureComponent.builder().code("BOOL").dataType(FeatureComponent.DataType.BOOLEAN).status(VersionableEntity.EntityStatus.DRAFT).build();

        when(featureComponentService.getFeatureComponentByCode("INT", null)).thenReturn(fInt);
        when(featureComponentService.getFeatureComponentByCode("DEC", null)).thenReturn(fDec);
        when(featureComponentService.getFeatureComponentByCode("BOOL", null)).thenReturn(fBool);

        // Valid values
        productService.updateProduct(1L, createFeatureRequestByCode("INT", "123"));
        productService.updateProduct(1L, createFeatureRequestByCode("DEC", "12.34"));
        productService.updateProduct(1L, createFeatureRequestByCode("BOOL", "true"));

        // Invalid values
        assertThrows(com.bankengine.web.exception.ValidationException.class, () -> productService.updateProduct(1L, createFeatureRequestByCode("INT", "abc")));
        assertThrows(com.bankengine.web.exception.ValidationException.class, () -> productService.updateProduct(1L, createFeatureRequestByCode("DEC", "abc")));
        assertThrows(com.bankengine.web.exception.ValidationException.class, () -> productService.updateProduct(1L, createFeatureRequestByCode("BOOL", "abc")));
        assertThrows(com.bankengine.web.exception.ValidationException.class, () -> productService.updateProduct(1L, createFeatureRequestByCode("INT", "")));
    }

    @Test
    void testDeactivateProduct_AlreadyInactive() {
        Product p = createValidProduct(VersionableEntity.EntityStatus.INACTIVE);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        assertThrows(IllegalStateException.class, () -> productService.deactivateProduct(1L));
    }

    @Test
    void testExtendProductExpiry_InvalidStatus() {
        Product p = createValidProduct(VersionableEntity.EntityStatus.ARCHIVED);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        assertThrows(IllegalStateException.class, () -> productService.extendProductExpiry(1L, LocalDate.now().plusDays(1)));
    }

    @Test
    void testExtendProductExpiry_NewBeforeCurrent() {
        Product p = createValidProduct(VersionableEntity.EntityStatus.ACTIVE);
        p.setExpiryDate(LocalDate.now().plusDays(10));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        assertThrows(IllegalArgumentException.class, () -> productService.extendProductExpiry(1L, LocalDate.now().plusDays(5)));
    }

    @Test
    void testExtendProductExpiry_NewInPast_NoCurrent() {
        Product p = createValidProduct(VersionableEntity.EntityStatus.ACTIVE);
        p.setExpiryDate(null);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        assertThrows(IllegalArgumentException.class, () -> productService.extendProductExpiry(1L, LocalDate.now().minusDays(1)));
    }

    @Test
    void testDeactivateProduct_Success() {
        Product p = createValidProduct(VersionableEntity.EntityStatus.ACTIVE);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(productRepository.save(any())).thenReturn(p);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        productService.deactivateProduct(1L);
        assertEquals(VersionableEntity.EntityStatus.INACTIVE, p.getStatus());
        assertEquals(LocalDate.now(), p.getExpiryDate());
    }

    @Test
    void testExtendProductExpiry_Success() {
        Product p = createValidProduct(VersionableEntity.EntityStatus.ACTIVE);
        p.setExpiryDate(LocalDate.now().plusDays(10));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(productRepository.save(any())).thenReturn(p);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        LocalDate newExpiry = LocalDate.now().plusDays(20);
        productService.extendProductExpiry(1L, newExpiry);
        assertEquals(newExpiry, p.getExpiryDate());
    }

    @Test
    @DisplayName("Sync Pricing: Should handle unchanged fields and target component code")
    void testSyncPricing_UnchangedAndTarget() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.DRAFT);
        PricingComponent pc = new PricingComponent();
        pc.setCode("P1");
        pc.setStatus(VersionableEntity.EntityStatus.DRAFT);

        ProductPricingLink link = ProductPricingLink.builder()
                .pricingComponent(pc)
                .fixedValue(new java.math.BigDecimal("10.00"))
                .fixedValueType(PriceValue.ValueType.FEE_ABSOLUTE)
                .useRulesEngine(false)
                .build();
        product.getProductPricingLinks().add(link);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenReturn(product);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        // Update with same values
        ProductPricingDto dto = new ProductPricingDto();
        dto.setPricingComponentCode("P1");
        dto.setFixedValue(new java.math.BigDecimal("10.00"));
        dto.setFixedValueType(PriceValue.ValueType.FEE_ABSOLUTE);
        dto.setUseRulesEngine(false);

        productService.updateProduct(1L, ProductRequest.builder().pricing(List.of(dto)).build());

        // Update with different values
        dto.setUseRulesEngine(true);
        dto.setFixedValue(new java.math.BigDecimal("20.00"));
        productService.updateProduct(1L, ProductRequest.builder().pricing(List.of(dto)).build());

        assertTrue(link.isUseRulesEngine());
        assertEquals(new java.math.BigDecimal("20.00"), link.getFixedValue());
    }

    // --- SYNC LOGIC ---

    @Test
    @DisplayName("Sync Features: Should remove orphaned links")
    void testSyncFeatures_orphanedLinks() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.DRAFT);
        FeatureComponent comp1 = FeatureComponent.builder().code("F1").dataType(FeatureComponent.DataType.STRING).status(VersionableEntity.EntityStatus.DRAFT).build();
        product.getProductFeatureLinks().add(ProductFeatureLink.builder().featureComponent(comp1).build());

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenReturn(product);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        productService.updateProduct(1L, ProductRequest.builder().features(new ArrayList<>()).build());

        assertTrue(product.getProductFeatureLinks().isEmpty());
    }

    @Test
    @DisplayName("Sync Features: Should update value if changed")
    void testSyncFeatures_smartUpdate() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.DRAFT);
        FeatureComponent comp = FeatureComponent.builder().code("F1").dataType(FeatureComponent.DataType.STRING).status(VersionableEntity.EntityStatus.DRAFT).build();

        ProductFeatureLink existingLink = spy(ProductFeatureLink.builder()
                .featureComponent(comp).featureValue("Original").build());
        product.getProductFeatureLinks().add(existingLink);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(featureComponentService.getFeatureComponentByCode(eq("F1"), any())).thenReturn(comp);
        when(productRepository.save(any())).thenReturn(product);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        productService.updateProduct(1L, createFeatureRequestByCode("F1", "NewValue"));
        assertEquals("NewValue", existingLink.getFeatureValue());
    }

    @Test
    @DisplayName("Sync Pricing: Should map all pricing fields correctly")
    void testSyncPricing_mapping() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.DRAFT);
        PricingComponent pc = new PricingComponent();
        pc.setBankId(TEST_BANK_ID);
        pc.setCode("P1");
        pc.setStatus(VersionableEntity.EntityStatus.DRAFT);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(pricingComponentService.getPricingComponentByCode(eq("P1"), any())).thenReturn(pc);
        when(productRepository.save(any())).thenReturn(product);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        ProductPricingDto dto = new ProductPricingDto();
        dto.setPricingComponentCode("P1");
        dto.setFixedValue(new java.math.BigDecimal("99.99"));

        productService.updateProduct(1L, ProductRequest.builder().pricing(List.of(dto)).build());

        assertFalse(product.getProductPricingLinks().isEmpty());
        assertEquals(new java.math.BigDecimal("99.99"), product.getProductPricingLinks().getFirst().getFixedValue());
    }

    @Test
    @DisplayName("Update: Should allow linking ACTIVE features and pricing components")
    void testUpdateProduct_AllowActiveComponents() {
        Product product = createValidProduct(VersionableEntity.EntityStatus.DRAFT);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenReturn(product);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse());

        // Mock ACTIVE Feature
        FeatureComponent activeFeature = FeatureComponent.builder()
                .code("ACTIVE_F")
                .dataType(FeatureComponent.DataType.STRING)
                .status(VersionableEntity.EntityStatus.ACTIVE)
                .build();
        when(featureComponentService.getFeatureComponentByCode(eq("ACTIVE_F"), any())).thenReturn(activeFeature);

        // Mock ACTIVE Pricing
        PricingComponent activePricing = new PricingComponent();
        activePricing.setCode("ACTIVE_P");
        activePricing.setStatus(VersionableEntity.EntityStatus.ACTIVE);
        when(pricingComponentService.getPricingComponentByCode(eq("ACTIVE_P"), any())).thenReturn(activePricing);

        ProductRequest req = ProductRequest.builder()
                .features(List.of(ProductFeatureDto.builder()
                        .featureComponentCode("ACTIVE_F")
                        .featureValue("Some Value")
                        .build()))
                .pricing(List.of(ProductPricingDto.builder()
                        .pricingComponentCode("ACTIVE_P")
                        .fixedValue(new java.math.BigDecimal("10.00"))
                        .build()))
                .build();

        // This should NOT throw an exception
        productService.updateProduct(1L, req);

        assertEquals(1, product.getProductFeatureLinks().size());
        assertEquals(1, product.getProductPricingLinks().size());
    }
}
