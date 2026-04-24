package com.bankengine.catalog.service;

import com.bankengine.catalog.converter.ProductBundleMapper;
import com.bankengine.catalog.dto.ProductBundleRequest;
import com.bankengine.catalog.dto.ProductBundleResponse;
import com.bankengine.catalog.dto.VersionRequest;
import com.bankengine.catalog.model.BundleProductLink;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.repository.ProductBundleRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.service.PricingComponentService;
import com.bankengine.test.config.BaseServiceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductBundleServiceTest extends BaseServiceTest {

    @Mock private ProductBundleRepository bundleRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CatalogConstraintService constraintService;
    @Mock private ProductBundleMapper bundleMapper;
    @Mock private PricingComponentService pricingComponentService;

    @InjectMocks
    private ProductBundleService bundleService;

    // --- HELPERS ---

    private ProductBundle createValidBundle(VersionableEntity.EntityStatus status) {
        ProductBundle bundle = new ProductBundle();
        bundle.setBankId(TEST_BANK_ID);
        bundle.setContainedProducts(new ArrayList<>());
        bundle.setBundlePricingLinks(new ArrayList<>());
        bundle.setStatus(status);
        return bundle;
    }

    private Product createValidProduct(Long id) {
        Product product = new Product();
        product.setId(id);
        product.setBankId(TEST_BANK_ID); // CRITICAL: Security check relies on this
        product.setStatus(VersionableEntity.EntityStatus.ACTIVE);
        return product;
    }

    // --- READ OPERATIONS ---

    @Test
    @DisplayName("Read: Should return response for valid ID")
    void getBundleResponseById_Success() {
        ProductBundle bundle = createValidBundle(VersionableEntity.EntityStatus.DRAFT);
        when(bundleRepository.findById(1L)).thenReturn(Optional.of(bundle));
        when(bundleMapper.toResponse(bundle)).thenReturn(new ProductBundleResponse());

        assertNotNull(bundleService.getBundleResponseById(1L));
    }

    // --- CREATE OPERATIONS ---

    @Test
    @DisplayName("Create Bundle: Should successfully initialize as DRAFT with single product")
    void createBundle_Success() {
        ProductBundleRequest request = new ProductBundleRequest();
        request.setName("Gold Bundle");
        request.setCode("GOLD-01");
        ProductBundleRequest.BundleProduct item = new ProductBundleRequest.BundleProduct();
        item.setProductCode("P-101");
        request.setProducts(List.of(item));

        ProductBundle bundle = createValidBundle(VersionableEntity.EntityStatus.DRAFT);
        Product product = createValidProduct(101L);
        product.setCode("P-101");

        when(bundleMapper.toEntity(any())).thenReturn(bundle);
        when(productRepository.findFirstByBankIdAndCodeOrderByVersionDesc(any(), eq("P-101"))).thenReturn(Optional.of(product));
        when(bundleMapper.toLink(any())).thenReturn(new BundleProductLink());
        when(bundleRepository.save(any())).thenReturn(bundle);
        when(bundleMapper.toResponse(any())).thenReturn(new ProductBundleResponse());

        assertNotNull(bundleService.createBundle(request));
        assertEquals(VersionableEntity.EntityStatus.DRAFT, bundle.getStatus());
    }

    @Test
    @DisplayName("Create Bundle: Should throw IllegalArgumentException when multiple main accounts provided")
    void createBundle_MultipleMainAccounts_ThrowsException() {
        ProductBundleRequest request = new ProductBundleRequest();
        ProductBundleRequest.BundleProduct p1 = new ProductBundleRequest.BundleProduct();
        p1.setMainAccount(true);
        ProductBundleRequest.BundleProduct p2 = new ProductBundleRequest.BundleProduct();
        p2.setMainAccount(true);
        request.setProducts(List.of(p1, p2));

        when(bundleMapper.toEntity(request)).thenReturn(createValidBundle(VersionableEntity.EntityStatus.DRAFT));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> bundleService.createBundle(request));
        assertEquals("A bundle can only have 1 Main Account item.", ex.getMessage());
    }

    @Test
    @DisplayName("Create Bundle: Should skip product attachment if products list is null")
    void createBundle_NullProducts_Success() {
        ProductBundleRequest request = new ProductBundleRequest();
        request.setProducts(null);
        ProductBundle bundle = createValidBundle(VersionableEntity.EntityStatus.DRAFT);

        when(bundleMapper.toEntity(request)).thenReturn(bundle);
        when(bundleRepository.save(any())).thenReturn(bundle);
        when(bundleMapper.toResponse(any())).thenReturn(new ProductBundleResponse());

        bundleService.createBundle(request);

        verify(bundleMapper, never()).toLink(any());
    }

    // --- UPDATE & VERSIONING ---

    @Test
    @DisplayName("Update Bundle: Should allow modifications only on DRAFT status")
    void updateBundle_DraftOnlyGuard() {
        ProductBundle activeBundle = createValidBundle(VersionableEntity.EntityStatus.DRAFT);
        activeBundle.setStatus(VersionableEntity.EntityStatus.ACTIVE);
        when(bundleRepository.findById(1L)).thenReturn(Optional.of(activeBundle));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> bundleService.updateBundle(1L, new ProductBundleRequest()));
        assertEquals("Operation allowed only on DRAFT status.", ex.getMessage());
    }

    @Test
    @DisplayName("Versioning: Revision (Same Code) should increment version and create DRAFT revision")
    void versionBundle_Revision_Success() {
        // Arrange: Source is ACTIVE with an old date
        LocalDate oldDate = LocalDate.now().minusMonths(6);
        ProductBundle source = createValidBundle(VersionableEntity.EntityStatus.ACTIVE);
        source.setCode("BNDL-001");
        source.setVersion(1);
        source.setActivationDate(oldDate);

        ProductBundle newVersion = createValidBundle(VersionableEntity.EntityStatus.DRAFT);
        LocalDate newActivationDate = LocalDate.now().plusDays(30);

        // Request with same code (or null code) triggers Revision logic
        VersionRequest request = new VersionRequest("Updated Name", null, newActivationDate, null);

        when(bundleRepository.findById(1L)).thenReturn(Optional.of(source));
        when(bundleMapper.clone(source)).thenReturn(newVersion);
        when(bundleRepository.save(any())).thenReturn(newVersion);
        when(bundleMapper.toResponse(any())).thenReturn(new ProductBundleResponse());

        // Act
        bundleService.versionBundle(1L, request);

        // Assert: Lineage & Status
        assertEquals(2, newVersion.getVersion());
        assertEquals(VersionableEntity.EntityStatus.DRAFT, newVersion.getStatus());
        assertEquals(VersionableEntity.EntityStatus.ACTIVE, source.getStatus(), "Source remains unchanged until revision activation");

        // Assert: Metadata & Temporal
        assertEquals("Updated Name", newVersion.getName());
    }

    @Test
    @DisplayName("Versioning: Branching (New Code) should reset version to 1, default to DRAFT, and leave source untouched")
    void versionBundle_Branching_Success() {
        // Arrange
        ProductBundle source = createValidBundle(VersionableEntity.EntityStatus.ACTIVE);
        source.setCode("ORIGINAL-CODE");
        source.setVersion(5);

        ProductBundle branch = createValidBundle(VersionableEntity.EntityStatus.ACTIVE);
        VersionRequest request = new VersionRequest("Branched Name", "NEW-CODE", null, null);

        when(bundleRepository.findById(1L)).thenReturn(Optional.of(source));
        when(bundleMapper.clone(source)).thenReturn(branch);
        when(bundleRepository.save(any())).thenReturn(branch);
        when(bundleMapper.toResponse(any())).thenReturn(new ProductBundleResponse());

        // Act
        bundleService.versionBundle(1L, request);

        // Assert
        assertEquals("NEW-CODE", branch.getCode());
        assertEquals(1, branch.getVersion(), "Branches must restart at version 1");
        assertEquals(VersionableEntity.EntityStatus.DRAFT, branch.getStatus(), "Branches must start as DRAFT");
        assertEquals(VersionableEntity.EntityStatus.ACTIVE, source.getStatus(), "Source should remain ACTIVE when branching to a new code");
    }

    @Test
    @DisplayName("Versioning: Should throw AccessDeniedException if bankId is wrong")
    void versionBundle_WrongBankId_ThrowsException() {
        ProductBundle source = createValidBundle(VersionableEntity.EntityStatus.DRAFT);
        source.setBankId("ATTACKER_BANK");
        when(bundleRepository.findById(1L)).thenReturn(Optional.of(source));
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> bundleService.versionBundle(1L, new VersionRequest()));
        assertEquals("You do not have permission to access this Bundle", ex.getMessage());
    }

    // --- ACTIVATION & CONSTRAINTS ---

    @Test
    @DisplayName("Activate Bundle: Should fail if any constituent product is not ACTIVE")
    void activateBundle_InactiveProduct_ThrowsException() {
        ProductBundle bundle = createValidBundle(VersionableEntity.EntityStatus.DRAFT);

        Product draftProduct = createValidProduct(10L);
        draftProduct.setName("Draft Loan");
        draftProduct.setStatus(VersionableEntity.EntityStatus.DRAFT);

        BundleProductLink link = BundleProductLink.builder()
                .product(draftProduct).mainAccount(true).build();
        bundle.getContainedProducts().add(link);

        when(bundleRepository.findById(1L)).thenReturn(Optional.of(bundle));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> bundleService.activateBundle(1L));
        assertEquals("Cannot activate bundle. Products must be ACTIVE: " + draftProduct.getName(), ex.getMessage());
    }

    @Test
    @DisplayName("Activate Bundle: Should default activation date to TODAY if missing or in past")
    void activateBundle_AdjustsPastDate() {
        ProductBundle bundle = createValidBundle(VersionableEntity.EntityStatus.DRAFT);
        bundle.setActivationDate(LocalDate.now().minusDays(10));

        Product activeProd = new Product();
        activeProd.setStatus(VersionableEntity.EntityStatus.ACTIVE);
        bundle.getContainedProducts().add(BundleProductLink.builder().product(activeProd).mainAccount(true).build());

        when(bundleRepository.findById(1L)).thenReturn(Optional.of(bundle));
        when(bundleRepository.save(any())).thenReturn(bundle);
        when(bundleMapper.toResponse(any())).thenReturn(new ProductBundleResponse());

        bundleService.activateBundle(1L);

        assertEquals(LocalDate.now(), bundle.getActivationDate());
        assertEquals(VersionableEntity.EntityStatus.ACTIVE, bundle.getStatus());
    }

    @Test
    @DisplayName("Activate Bundle: Should throw error if bundle has no products")
    void activateBundle_EmptyProducts_ThrowsException() {
        ProductBundle bundle = createValidBundle(VersionableEntity.EntityStatus.DRAFT);

        when(bundleRepository.findById(1L)).thenReturn(Optional.of(bundle));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> bundleService.activateBundle(1L));
        assertEquals("Cannot activate a bundle with no products.", ex.getMessage());
    }

    @Test
    @DisplayName("Activate Bundle: Should throw error if no Main Account is present")
    void activateBundle_NoMainAccount_ThrowsException() {
        ProductBundle bundle = createValidBundle(VersionableEntity.EntityStatus.DRAFT);
        bundle.getContainedProducts().add(
                BundleProductLink.builder().product(Product.builder().status(VersionableEntity.EntityStatus.ACTIVE).build())
                        .mainAccount(false).build()
        );

        when(bundleRepository.findById(1L)).thenReturn(Optional.of(bundle));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> bundleService.activateBundle(1L));
        assertEquals("Bundle must have exactly 1 Main Account.", ex.getMessage());
    }

    @Test
    @DisplayName("Compatibility: Should accumulate products for sequential category validation")
    void createBundle_ValidatesCategoryCompatibilitySequentially() {
        ProductBundleRequest request = new ProductBundleRequest();
        ProductBundleRequest.BundleProduct item1 = new ProductBundleRequest.BundleProduct();
        item1.setProductCode("P-10");
        ProductBundleRequest.BundleProduct item2 = new ProductBundleRequest.BundleProduct();
        item2.setProductCode("P-20");
        request.setProducts(List.of(item1, item2));

        Product p1 = createValidProduct(10L);
        p1.setCode("P-10");
        Product p2 = createValidProduct(20L);
        p2.setCode("P-20");

        ProductBundle bundle = createValidBundle(VersionableEntity.EntityStatus.DRAFT);

        when(bundleMapper.toEntity(any())).thenReturn(bundle);
        when(productRepository.findFirstByBankIdAndCodeOrderByVersionDesc(any(), eq("P-10"))).thenReturn(Optional.of(p1));
        when(productRepository.findFirstByBankIdAndCodeOrderByVersionDesc(any(), eq("P-20"))).thenReturn(Optional.of(p2));
        when(bundleMapper.toLink(any())).thenReturn(new BundleProductLink());
        when(bundleRepository.save(any())).thenReturn(bundle);
        when(bundleMapper.toResponse(any())).thenReturn(new ProductBundleResponse());

        bundleService.createBundle(request);

        verify(constraintService).validateCategoryCompatibility(eq(p2), argThat(list -> list.contains(p1)));
    }

    @Test
    @DisplayName("Archive: Should set status to INACTIVE and expiry to TODAY")
    void archiveBundle_Success() {
        ProductBundle bundle = createValidBundle(VersionableEntity.EntityStatus.DRAFT);
        bundle.setStatus(VersionableEntity.EntityStatus.DRAFT);
        when(bundleRepository.findById(1L)).thenReturn(Optional.of(bundle));

        bundleService.archiveBundle(1L);

        assertEquals(VersionableEntity.EntityStatus.ARCHIVED, bundle.getStatus());
        assertEquals(LocalDate.now(), bundle.getExpiryDate());
    }

    @Test
    @DisplayName("Archive: Should not change expiry date if it is already in the past")
    void archiveBundle_KeepsPastExpiryDate() {
        LocalDate pastDate = LocalDate.now().minusDays(5);
        ProductBundle bundle = createValidBundle(VersionableEntity.EntityStatus.DRAFT);
        bundle.setStatus(VersionableEntity.EntityStatus.DRAFT);
        bundle.setExpiryDate(pastDate);

        when(bundleRepository.findById(1L)).thenReturn(Optional.of(bundle));

        bundleService.archiveBundle(1L);

        assertEquals(pastDate, bundle.getExpiryDate());
        assertEquals(VersionableEntity.EntityStatus.ARCHIVED, bundle.getStatus());
    }

    @Test
    @DisplayName("Branch: updateBundle success with products and pricing")
    void testUpdateBundle_full() {
        ProductBundle bundle = createValidBundle(VersionableEntity.EntityStatus.DRAFT);
        bundle.setId(1L);
        when(bundleRepository.findById(1L)).thenReturn(Optional.of(bundle));
        when(bundleRepository.save(any())).thenReturn(bundle);
        when(bundleMapper.toResponse(any())).thenReturn(new ProductBundleResponse());

        ProductBundleRequest request = new ProductBundleRequest();
        request.setName("Updated Name");
        request.setProducts(new ArrayList<>());
        request.setPricing(new ArrayList<>());

        bundleService.updateBundle(1L, request);

        assertEquals("Updated Name", bundle.getName());
        verify(bundleRepository).save(bundle);
    }

    @Test
    @DisplayName("Branch: syncBundlePricingInternal - update and remove")
    void testSyncBundlePricingInternal() {
        ProductBundle bundle = createValidBundle(VersionableEntity.EntityStatus.DRAFT);

        PricingComponent pc1 = new PricingComponent(); pc1.setCode("PC1");
        PricingComponent pc2 = new PricingComponent(); pc2.setCode("PC2");

        com.bankengine.pricing.model.BundlePricingLink link1 = new com.bankengine.pricing.model.BundlePricingLink();
        link1.setPricingComponent(pc1);
        link1.setProductBundle(bundle);
        bundle.getBundlePricingLinks().add(link1);

        com.bankengine.catalog.dto.ProductPricingDto dto1 = new com.bankengine.catalog.dto.ProductPricingDto();
        dto1.setPricingComponentCode("PC1");
        dto1.setFixedValue(new java.math.BigDecimal("10.00"));

        com.bankengine.catalog.dto.ProductPricingDto dto2 = new com.bankengine.catalog.dto.ProductPricingDto();
        dto2.setPricingComponentCode("PC2");
        dto2.setFixedValue(new java.math.BigDecimal("20.00"));

        when(bundleRepository.findById(1L)).thenReturn(Optional.of(bundle));
        when(pricingComponentService.getPricingComponentByCode("PC2", null)).thenReturn(pc2);
        when(bundleRepository.save(any())).thenReturn(bundle);
        when(bundleMapper.toResponse(any())).thenReturn(new ProductBundleResponse());

        ProductBundleRequest request = new ProductBundleRequest();
        request.setPricing(List.of(dto1, dto2));

        bundleService.updateBundle(1L, request);

        assertEquals(2, bundle.getBundlePricingLinks().size());
        assertTrue(bundle.getBundlePricingLinks().stream().anyMatch(l -> l.getPricingComponent().getCode().equals("PC1") && l.getFixedValue().equals(new java.math.BigDecimal("10.00"))));
    }

    @Test
    @DisplayName("Branch: mapBundlePricingFields - validation errors")
    void testMapBundlePricingFields_Validations() {
        ProductBundle bundle = createValidBundle(VersionableEntity.EntityStatus.DRAFT);
        when(bundleRepository.findById(1L)).thenReturn(Optional.of(bundle));

        com.bankengine.catalog.dto.ProductPricingDto dto = new com.bankengine.catalog.dto.ProductPricingDto();
        dto.setPricingComponentCode("PC1");
        dto.setEffectiveDate(LocalDate.now().minusDays(1));

        ProductBundleRequest request = new ProductBundleRequest();
        request.setPricing(List.of(dto));

        PricingComponent pc = new PricingComponent(); pc.setCode("PC1");
        when(pricingComponentService.getPricingComponentByCode("PC1", null)).thenReturn(pc);

        assertThrows(IllegalArgumentException.class, () -> bundleService.updateBundle(1L, request));

        dto.setEffectiveDate(LocalDate.now().plusDays(1));
        dto.setExpiryDate(LocalDate.now());
        assertThrows(IllegalArgumentException.class, () -> bundleService.updateBundle(1L, request));
    }

    @Test
    @DisplayName("Branch: updateBundle - partial metadata")
    void testUpdateBundle_PartialMetadata() {
        ProductBundle bundle = createValidBundle(VersionableEntity.EntityStatus.DRAFT);
        when(bundleRepository.findById(1L)).thenReturn(Optional.of(bundle));
        when(bundleRepository.save(any())).thenReturn(bundle);
        when(bundleMapper.toResponse(any())).thenReturn(new ProductBundleResponse());

        ProductBundleRequest request = new ProductBundleRequest();
        request.setDescription("New Desc");
        request.setTargetCustomerSegments("RETAIL");

        bundleService.updateBundle(1L, request);

        assertEquals("New Desc", bundle.getDescription());
        assertEquals("RETAIL", bundle.getTargetCustomerSegments());
    }

    @Test
    @DisplayName("Read: getProductBundleByCode")
    void testGetProductBundleByCode() {
        ProductBundle bundle = new ProductBundle();
        bundle.setBankId(TEST_BANK_ID);
        when(bundleRepository.findByBankIdAndCodeAndVersion(TEST_BANK_ID, "CODE", 1)).thenReturn(Optional.of(bundle));

        ProductBundle result = bundleService.getProductBundleByCode("CODE", 1);
        assertEquals(bundle, result);
    }

    @Test
    @DisplayName("Read: getAllBundles")
    void testGetAllBundles() {
        ProductBundle bundle = new ProductBundle();
        org.springframework.data.domain.Page<ProductBundle> page = new org.springframework.data.domain.PageImpl<>(List.of(bundle));
        when(bundleRepository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(page);
        when(bundleMapper.toResponse(bundle)).thenReturn(new ProductBundleResponse());

        org.springframework.data.domain.Page<ProductBundleResponse> result = bundleService.getAllBundles(org.springframework.data.domain.Pageable.unpaged());
        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("Branch: activateBundle - already active or past date")
    void testActivateBundle_various() {
        ProductBundle bundle = createValidBundle(VersionableEntity.EntityStatus.DRAFT);
        bundle.setActivationDate(LocalDate.now().plusDays(10)); // Future date
        Product activeProd = createValidProduct(101L);
        bundle.getContainedProducts().add(BundleProductLink.builder().product(activeProd).mainAccount(true).build());
        when(bundleRepository.findById(1L)).thenReturn(Optional.of(bundle));
        when(bundleRepository.save(any())).thenReturn(bundle);
        when(bundleMapper.toResponse(any())).thenReturn(new ProductBundleResponse());

        bundleService.activateBundle(1L);
        assertEquals(LocalDate.now().plusDays(10), bundle.getActivationDate());
    }

    @Test
    @DisplayName("Branch: activateBundle - Constituent products must be ACTIVE")
    void testActivateBundle_inactiveProducts() {
        ProductBundle bundle = createValidBundle(VersionableEntity.EntityStatus.DRAFT);
        Product draftProd = createValidProduct(102L);
        draftProd.setName("DraftProd");
        draftProd.setStatus(VersionableEntity.EntityStatus.DRAFT);
        bundle.getContainedProducts().add(BundleProductLink.builder().product(draftProd).mainAccount(true).build());
        when(bundleRepository.findById(1L)).thenReturn(Optional.of(bundle));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> bundleService.activateBundle(1L));
        assertTrue(ex.getMessage().contains("Products must be ACTIVE: DraftProd"));
    }

    @Test
    @DisplayName("Branch: versionBundle - deep clone coverage")
    void testVersionBundle_deepClone() {
        ProductBundle source = createValidBundle(VersionableEntity.EntityStatus.ACTIVE);
        source.setCode("B1");
        source.setVersion(1);

        Product p = createValidProduct(10L);
        BundleProductLink pLink = new BundleProductLink();
        pLink.setProduct(p);
        pLink.setMainAccount(true);
        pLink.setMandatory(true);
        source.getContainedProducts().add(pLink);

        PricingComponent pc = new PricingComponent(); pc.setCode("PC");
        com.bankengine.pricing.model.BundlePricingLink prLink = new com.bankengine.pricing.model.BundlePricingLink();
        prLink.setPricingComponent(pc);
        source.getBundlePricingLinks().add(prLink);

        ProductBundle target = createValidBundle(VersionableEntity.EntityStatus.DRAFT);
        target.setBankId(TEST_BANK_ID);

        when(bundleRepository.findById(1L)).thenReturn(Optional.of(source));
        when(bundleMapper.clone(source)).thenReturn(target);
        when(bundleMapper.clonePricing(prLink)).thenReturn(new com.bankengine.pricing.model.BundlePricingLink());
        when(bundleRepository.existsByBankIdAndCodeAndVersion(any(), any(), anyInt())).thenReturn(false);
        when(bundleRepository.save(any())).thenReturn(target);
        when(bundleMapper.toResponse(any())).thenReturn(new ProductBundleResponse());

        bundleService.versionBundle(1L, new VersionRequest());

        assertEquals(1, target.getContainedProducts().size());
        assertEquals(1, target.getBundlePricingLinks().size());
    }

    @Test
    @DisplayName("Branch: activateBundle - already active logic")
    void testActivateBundle_alreadyActive() {
        ProductBundle bundle = createValidBundle(VersionableEntity.EntityStatus.DRAFT);
        bundle.setStatus(VersionableEntity.EntityStatus.DRAFT);
        bundle.setActivationDate(LocalDate.now());
        Product activeProd = createValidProduct(101L);
        bundle.getContainedProducts().add(BundleProductLink.builder().product(activeProd).mainAccount(true).build());

        when(bundleRepository.findById(1L)).thenReturn(Optional.of(bundle));
        when(bundleRepository.save(any())).thenReturn(bundle);
        when(bundleMapper.toResponse(any())).thenReturn(new ProductBundleResponse());

        bundleService.activateBundle(1L);
        assertEquals(VersionableEntity.EntityStatus.ACTIVE, bundle.getStatus());
    }

    @Test
    @DisplayName("Branch: syncBundlePricingInternal - update existing fields")
    void testSyncBundlePricingInternal_update() {
        ProductBundle bundle = createValidBundle(VersionableEntity.EntityStatus.DRAFT);

        PricingComponent pc = new PricingComponent(); pc.setCode("PC");
        com.bankengine.pricing.model.BundlePricingLink link = new com.bankengine.pricing.model.BundlePricingLink();
        link.setPricingComponent(pc);
        link.setFixedValue(new java.math.BigDecimal("5.00"));
        link.setFixedValueType(PriceValue.ValueType.DISCOUNT_PERCENTAGE);
        link.setUseRulesEngine(false);
        link.setEffectiveDate(LocalDate.now().plusDays(1));
        link.setExpiryDate(LocalDate.now().plusDays(10));
        bundle.getBundlePricingLinks().add(link);

        com.bankengine.catalog.dto.ProductPricingDto dto = new com.bankengine.catalog.dto.ProductPricingDto();
        dto.setPricingComponentCode("PC");
        dto.setFixedValue(new java.math.BigDecimal("10.00"));
        dto.setFixedValueType(PriceValue.ValueType.FEE_ABSOLUTE);
        dto.setUseRulesEngine(true);
        dto.setEffectiveDate(LocalDate.now().plusDays(2));
        dto.setExpiryDate(LocalDate.now().plusDays(11));

        when(bundleRepository.findById(1L)).thenReturn(Optional.of(bundle));
        when(bundleRepository.save(any())).thenReturn(bundle);
        when(bundleMapper.toResponse(any())).thenReturn(new ProductBundleResponse());

        ProductBundleRequest request = new ProductBundleRequest();
        request.setPricing(List.of(dto));

        bundleService.updateBundle(1L, request);

        assertEquals(new java.math.BigDecimal("10.00"), link.getFixedValue());
        assertEquals(PriceValue.ValueType.FEE_ABSOLUTE, link.getFixedValueType());
        assertTrue(link.isUseRulesEngine());
        assertEquals(LocalDate.now().plusDays(2), link.getEffectiveDate());
        assertEquals(LocalDate.now().plusDays(11), link.getExpiryDate());
    }

    @Test
    @DisplayName("Branch: syncBundlePricingInternal - creation with existing and new")
    void testSyncBundlePricingInternal_complex() {
        ProductBundle bundle = createValidBundle(VersionableEntity.EntityStatus.DRAFT);

        PricingComponent pcExisting = new PricingComponent(); pcExisting.setCode("PC_EXISTING");
        PricingComponent pcNew = new PricingComponent(); pcNew.setCode("PC_NEW");

        com.bankengine.pricing.model.BundlePricingLink linkExisting = new com.bankengine.pricing.model.BundlePricingLink();
        linkExisting.setPricingComponent(pcExisting);
        linkExisting.setProductBundle(bundle);
        bundle.getBundlePricingLinks().add(linkExisting);

        com.bankengine.catalog.dto.ProductPricingDto dtoExisting = new com.bankengine.catalog.dto.ProductPricingDto();
        dtoExisting.setPricingComponentCode("PC_EXISTING");
        dtoExisting.setFixedValue(new java.math.BigDecimal("10.00"));
        dtoExisting.setFixedValueType(PriceValue.ValueType.FEE_ABSOLUTE);
        dtoExisting.setUseRulesEngine(true);
        dtoExisting.setEffectiveDate(LocalDate.now().plusDays(5));
        dtoExisting.setExpiryDate(LocalDate.now().plusDays(10));

        com.bankengine.catalog.dto.ProductPricingDto dtoNew = new com.bankengine.catalog.dto.ProductPricingDto();
        dtoNew.setPricingComponentCode("PC_NEW");
        dtoNew.setFixedValue(new java.math.BigDecimal("20.00"));

        when(bundleRepository.findById(1L)).thenReturn(Optional.of(bundle));
        when(pricingComponentService.getPricingComponentByCode("PC_NEW", null)).thenReturn(pcNew);
        when(bundleRepository.save(any())).thenReturn(bundle);
        when(bundleMapper.toResponse(any())).thenReturn(new ProductBundleResponse());

        ProductBundleRequest request = new ProductBundleRequest();
        request.setPricing(List.of(dtoExisting, dtoNew));

        bundleService.updateBundle(1L, request);

        assertEquals(2, bundle.getBundlePricingLinks().size());
    }

    @Test
    @DisplayName("Branch: activateBundle - activation date logic")
    void testActivateBundle_activationDateLogic() {
        ProductBundle bundle = createValidBundle(VersionableEntity.EntityStatus.DRAFT);
        bundle.setActivationDate(LocalDate.now().minusDays(1)); // Past date
        Product activeProd = createValidProduct(101L);
        bundle.getContainedProducts().add(BundleProductLink.builder().product(activeProd).mainAccount(true).build());
        when(bundleRepository.findById(1L)).thenReturn(Optional.of(bundle));
        when(bundleRepository.save(any())).thenReturn(bundle);
        when(bundleMapper.toResponse(any())).thenReturn(new ProductBundleResponse());

        bundleService.activateBundle(1L);
        assertEquals(LocalDate.now(), bundle.getActivationDate());
    }
}
