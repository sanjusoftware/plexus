package com.bankengine.catalog.service;

import com.bankengine.catalog.dto.ProductBundleRequest;
import com.bankengine.catalog.model.BundleProductLink;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.repository.BundleProductLinkRepository;
import com.bankengine.catalog.repository.ProductBundleRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.test.config.BaseServiceTest;
import com.bankengine.web.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductBundleServiceTest extends BaseServiceTest {

    @Mock
    private ProductBundleRepository bundleRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private BundleProductLinkRepository bundleProductLinkRepository;
    @Mock
    private CatalogConstraintService constraintService;

    @InjectMocks
    private ProductBundleService bundleService;

    @Test
    @DisplayName("Activate Bundle - Should fail when any linked product is still in DRAFT status")
    void activateBundle_ShouldFail_WhenProductIsDraft() {
        Long bundleId = 1L;
        ProductBundle bundle = new ProductBundle();
        bundle.setStatus(ProductBundle.BundleStatus.DRAFT);
        Product draftProduct = new Product();
        draftProduct.setName("Draft Loan");
        draftProduct.setStatus("DRAFT");
        bundle.setContainedProducts(List.of(new BundleProductLink(bundle, draftProduct, true, true)));

        when(bundleRepository.findById(bundleId)).thenReturn(Optional.of(bundle));

        assertThrows(IllegalStateException.class, () -> bundleService.activateBundle(bundleId));
    }

    @Test
    @DisplayName("Update Bundle - Should archive current version and create a new record")
    void updateBundle_ShouldArchiveOldAndCreateNew() {
        Long oldId = 1L;
        ProductBundle oldBundle = new ProductBundle();
        oldBundle.setStatus(ProductBundle.BundleStatus.ACTIVE);

        ProductBundleRequest request = new ProductBundleRequest();
        request.setName("New Version");
        request.setProducts(List.of());

        when(bundleRepository.findById(oldId)).thenReturn(Optional.of(oldBundle));
        when(bundleRepository.save(any(ProductBundle.class))).thenAnswer(invocation -> {
            ProductBundle b = invocation.getArgument(0);
            if (b.getId() == null) b.setId(2L);
            return b;
        });

        Long newId = bundleService.updateBundle(oldId, request);

        assertEquals(ProductBundle.BundleStatus.ARCHIVED, oldBundle.getStatus());
        assertNotNull(newId);
    }

    @Test
    @DisplayName("Clone Bundle - Ensure deep copy of links and initialization to DRAFT")
    void cloneBundle_DeepCopyCheck() {
        ProductBundle source = new ProductBundle();
        source.setId(1L);
        source.setName("Source Bundle");
        source.setCode("SRC-01");

        Product p = new Product();
        p.setId(10L);
        // Use ArrayList to avoid Immutable List issues if the service tries to add to it
        source.setContainedProducts(new ArrayList<>(List.of(new BundleProductLink(source, p, true, true))));

        when(bundleRepository.findById(1L)).thenReturn(Optional.of(source));
        when(bundleRepository.save(any(ProductBundle.class))).thenAnswer(i -> {
            ProductBundle b = i.getArgument(0);
            b.setId(2L);
            return b;
        });

        bundleService.cloneBundle(1L, "New Bundle Name");

        verify(bundleRepository).save(argThat(b ->
            "New Bundle Name".equals(b.getName()) &&
            b.getStatus() == ProductBundle.BundleStatus.DRAFT &&
            b.getBankId().equals(TEST_BANK_ID) &&
            b.getCode() != null && b.getCode().contains("SRC-01")
        ));

        verify(bundleProductLinkRepository).save(argThat(link ->
                link.getProduct().getId().equals(10L) &&
                link.getProductBundle().getName().equals("New Bundle Name")
        ));
    }

    @Test
    void activateBundle_ShouldThrowError_WhenProductIsInactive() {
        // Arrange
        Product inactiveProduct = new Product();
        inactiveProduct.setName("Home Loan");
        inactiveProduct.setStatus("DRAFT"); // Not ACTIVE

        ProductBundle bundle = new ProductBundle();
        bundle.setStatus(ProductBundle.BundleStatus.DRAFT);
        bundle.setContainedProducts(List.of(new BundleProductLink(bundle, inactiveProduct, true, true)));

        when(bundleRepository.findById(1L)).thenReturn(Optional.of(bundle));

        // Act & Assert
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> bundleService.activateBundle(1L));

        assertEquals("Cannot activate bundle. Products must be ACTIVE: Home Loan", ex.getMessage());
    }

    @Test
    @DisplayName("Create Bundle - Fail when multiple Main Accounts provided")
    void createBundle_ShouldThrowException_WhenMultipleMainAccounts() {
        ProductBundleRequest request = new ProductBundleRequest();
        ProductBundleRequest.BundleProduct item1 = new ProductBundleRequest.BundleProduct();
        item1.setMainAccount(true);
        ProductBundleRequest.BundleProduct item2 = new ProductBundleRequest.BundleProduct();
        item2.setMainAccount(true);
        request.setProducts(List.of(item1, item2));

        assertThrows(IllegalArgumentException.class, () -> bundleService.createBundle(request));
    }

    @Test
    @DisplayName("Clone Bundle - Fail when source has data integrity issue (Multiple Mains)")
    void cloneBundle_ShouldThrowException_WhenSourceHasMultipleMainAccounts() {
        // Arrange: Simulate a database state that violates the rule
        ProductBundle source = new ProductBundle();
        source.setId(1L);

        // Create two links marked as Main Account
        BundleProductLink link1 = new BundleProductLink(source, new Product(), true, true);
        BundleProductLink link2 = new BundleProductLink(source, new Product(), true, true);

        source.setContainedProducts(List.of(link1, link2));

        when(bundleRepository.findById(1L)).thenReturn(Optional.of(source));

        // Act & Assert
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> bundleService.cloneBundle(1L, "New Name"));

        assertTrue(ex.getMessage().contains("Data Integrity Error"));
        verify(bundleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should call compatibility check with accumulated list of products")
    void createBundle_ShouldAccumulateProductsForCompatibilityCheck() {
        // Arrange
        ProductBundleRequest request = new ProductBundleRequest();
        request.setCode("B-001");
        request.setName("Test Bundle");

        ProductBundleRequest.BundleProduct item1 = new ProductBundleRequest.BundleProduct();
        item1.setProductId(101L);
        ProductBundleRequest.BundleProduct item2 = new ProductBundleRequest.BundleProduct();
        item2.setProductId(102L);
        ProductBundleRequest.BundleProduct item3 = new ProductBundleRequest.BundleProduct();
        item3.setProductId(103L);

        request.setProducts(List.of(item1, item2, item3));

        Product p1 = new Product();
        p1.setId(101L);
        p1.setCategory("CAT1");
        Product p2 = new Product();
        p2.setId(102L);
        p2.setCategory("CAT2");
        Product p3 = new Product();
        p3.setId(103L);
        p3.setCategory("CAT3");

        when(productRepository.findById(101L)).thenReturn(Optional.of(p1));
        when(productRepository.findById(102L)).thenReturn(Optional.of(p2));
        when(productRepository.findById(103L)).thenReturn(Optional.of(p3));

        when(bundleRepository.save(any(ProductBundle.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        bundleService.createBundle(request);

        // Assert
        // Item 1: Checked against empty list
        verify(constraintService).validateCategoryCompatibility(eq(p1), argThat(List::isEmpty));

        // Item 2: Checked against list containing p1
        verify(constraintService).validateCategoryCompatibility(eq(p2), argThat(list ->
                list.size() == 1 && list.contains(p1)));

        // Item 3: Checked against list containing p1 and p2
        verify(constraintService).validateCategoryCompatibility(eq(p3), argThat(list ->
                list.size() == 2 && list.contains(p1) && list.contains(p2)));
    }

    @Test
    @DisplayName("Create Bundle - Should fail and not save when category conflict is detected")
    void createBundle_ShouldThrowException_WhenCategoryConflictDetected() {
        // Arrange
        ProductBundleRequest request = new ProductBundleRequest();
        request.setName("Conflicting Bundle");

        ProductBundleRequest.BundleProduct item1 = new ProductBundleRequest.BundleProduct();
        item1.setProductId(101L);
        ProductBundleRequest.BundleProduct item2 = new ProductBundleRequest.BundleProduct();
        item2.setProductId(102L);
        request.setProducts(List.of(item1, item2));

        Product p1 = new Product();
        p1.setId(101L);
        p1.setCategory("RETAIL");

        Product p2 = new Product();
        p2.setId(102L);
        p2.setCategory("WEALTH");

        // Mock repository lookups
        when(productRepository.findById(101L)).thenReturn(Optional.of(p1));
        when(productRepository.findById(102L)).thenReturn(Optional.of(p2));

        // Mock a success for the first item, but a failure for the second item
        doNothing().when(constraintService).validateCategoryCompatibility(eq(p1), anyList());
        doThrow(new ValidationException("Business Conflict detected"))
                .when(constraintService).validateCategoryCompatibility(eq(p2), anyList());

        // We need to return the bundle for the first save call (the header)
        when(bundleRepository.save(any(ProductBundle.class))).thenAnswer(i -> i.getArgument(0));

        // Act & Assert
        assertThrows(ValidationException.class,
                () -> bundleService.createBundle(request));

        // Verify that we never tried to save a Link for the second (conflicting) product
        verify(bundleProductLinkRepository, never()).save(argThat(link ->
                link.getProduct().getId().equals(102L)));
    }

    @Test
    @DisplayName("Activate Bundle - Should fail for non-DRAFT bundles")
    void activateBundle_ShouldFail_WhenNotDraft() {
        Long bundleId = 1L;
        ProductBundle bundle = new ProductBundle();
        bundle.setStatus(ProductBundle.BundleStatus.ACTIVE);
        when(bundleRepository.findById(bundleId)).thenReturn(Optional.of(bundle));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> bundleService.activateBundle(bundleId));
        assertTrue(ex.getMessage().contains("Only DRAFT bundles can be activated"));
    }

    @Test
    @DisplayName("Activate Bundle - Should fail when bundle has no products")
    void activateBundle_ShouldFail_WhenEmpty() {
        Long bundleId = 1L;
        ProductBundle bundle = new ProductBundle();
        bundle.setStatus(ProductBundle.BundleStatus.DRAFT);
        bundle.setContainedProducts(List.of()); // Empty

        when(bundleRepository.findById(bundleId)).thenReturn(Optional.of(bundle));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> bundleService.activateBundle(bundleId));
        assertEquals("Cannot activate a bundle with no products.", ex.getMessage());
    }

    @Test
    @DisplayName("Activate Bundle - Should adjust activation date if set in the past")
    void activateBundle_ShouldAdjustPastDate() {
        Long bundleId = 1L;
        ProductBundle bundle = new ProductBundle();
        bundle.setStatus(ProductBundle.BundleStatus.DRAFT);
        bundle.setActivationDate(LocalDate.now().minusDays(5));

        Product activeProduct = new Product();
        activeProduct.setStatus("ACTIVE");
        bundle.setContainedProducts(List.of(new BundleProductLink(bundle, activeProduct, true, true)));

        when(bundleRepository.findById(bundleId)).thenReturn(Optional.of(bundle));

        bundleService.activateBundle(bundleId);

        assertEquals(LocalDate.now(), bundle.getActivationDate());
        assertEquals(ProductBundle.BundleStatus.ACTIVE, bundle.getStatus());
    }

    @Test
    @DisplayName("Archive Bundle - Should set status and expiry date correctly")
    void archiveBundle_ShouldHandleExpiryDate() {
        Long bundleId = 1L;
        ProductBundle bundle = new ProductBundle();
        bundle.setStatus(ProductBundle.BundleStatus.ACTIVE);
        bundle.setExpiryDate(LocalDate.now().plusDays(10));

        when(bundleRepository.findById(bundleId)).thenReturn(Optional.of(bundle));

        bundleService.archiveBundle(bundleId);

        assertEquals(ProductBundle.BundleStatus.ARCHIVED, bundle.getStatus());
        assertEquals(LocalDate.now(), bundle.getExpiryDate());
    }

    @Test
    @DisplayName("Archive Bundle - Handle null expiry date branch")
    void archiveBundle_ShouldHandleNullExpiry() {
        ProductBundle bundle = new ProductBundle();
        bundle.setStatus(ProductBundle.BundleStatus.ACTIVE);
        bundle.setExpiryDate(null);

        when(bundleRepository.findById(1L)).thenReturn(Optional.of(bundle));

        bundleService.archiveBundle(1L);

        assertEquals(ProductBundle.BundleStatus.ARCHIVED, bundle.getStatus());
        assertEquals(LocalDate.now(), bundle.getExpiryDate());
    }

    @Test
    @DisplayName("Create Bundle - Handle request with null items list")
    void createBundle_ShouldHandleNullItems() {
        ProductBundleRequest request = new ProductBundleRequest();
        request.setName("Empty Request Bundle");
        request.setProducts(null);

        when(bundleRepository.save(any(ProductBundle.class))).thenAnswer(i -> {
            ProductBundle b = i.getArgument(0);
            b.setId(100L);
            return b;
        });

        Long bundleId = bundleService.createBundle(request);

        assertNotNull(bundleId, "The returned bundle ID should not be null");
        assertEquals(100L, bundleId);
        verify(bundleProductLinkRepository, never()).save(any());
    }
}
