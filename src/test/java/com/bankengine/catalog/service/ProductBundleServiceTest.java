package com.bankengine.catalog.service;

import com.bankengine.auth.security.BankContextHolder;
import com.bankengine.catalog.dto.ProductBundleRequest;
import com.bankengine.catalog.model.BundleProductLink;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.repository.BundleProductLinkRepository;
import com.bankengine.catalog.repository.ProductBundleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductBundleServiceTest {

    @Mock private ProductBundleRepository bundleRepository;
    @Mock private BundleProductLinkRepository bundleProductLinkRepository;
    @InjectMocks private ProductBundleService bundleService;

    @BeforeEach
    void setUp() {
        // You need a way to set the context manually for the test thread
        // Assuming your BankContextHolder has a setBankId method:
        BankContextHolder.setBankId("TEST_BANK_001");
    }

    @AfterEach
    void tearDown() {
        // Always clear context to avoid leaking into other tests
        BankContextHolder.clear();
    }

    @Test
    void activateBundle_ShouldFail_WhenProductIsDraft() {
        // Arrange
        Long bundleId = 1L;
        ProductBundle bundle = new ProductBundle();
        bundle.setStatus(ProductBundle.BundleStatus.DRAFT);

        Product draftProduct = new Product();
        draftProduct.setName("Draft Loan");
        draftProduct.setStatus("DRAFT");

        BundleProductLink link = new BundleProductLink(bundle, draftProduct, true, true);
        bundle.setContainedProducts(List.of(link));

        when(bundleRepository.findById(bundleId)).thenReturn(Optional.of(bundle));

        // Act & Assert
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> bundleService.activateBundle(bundleId));

        assertTrue(ex.getMessage().contains("must be ACTIVE"));
        verify(bundleRepository, never()).save(any());
    }

    @Test
    void updateBundle_ShouldArchiveOldAndCreateNew() {
        // Arrange
        Long oldId = 1L;
        ProductBundle oldBundle = new ProductBundle();
        oldBundle.setStatus(ProductBundle.BundleStatus.ACTIVE);

        ProductBundleRequest request = new ProductBundleRequest();
        request.setName("New Version");
        request.setItems(List.of()); // Empty list for simple test

        when(bundleRepository.findById(oldId)).thenReturn(Optional.of(oldBundle));
        // Use thenAnswer to return the saved bundle with an ID
        when(bundleRepository.save(any(ProductBundle.class))).thenAnswer(invocation -> {
            ProductBundle b = invocation.getArgument(0);
            b.setId(2L);
            return b;
        });

        // Act
        Long newId = bundleService.updateBundle(oldId, request);

        // Assert
        assertEquals(ProductBundle.BundleStatus.ARCHIVED, oldBundle.getStatus());
        assertNotNull(newId);
        verify(bundleRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("Clone Bundle - Deep copy check for links and DRAFT status")
    void cloneBundle_DeepCopyCheck() {
        // Arrange
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
            b.setId(2L); // Set an ID to simulate saving
            return b;
        });

        // Act
        bundleService.cloneBundle(1L, "New Bundle Name");

        // Assert
        // Verify the Header save with specific property checks
        verify(bundleRepository).save(argThat(b -> {
            boolean nameMatches = "New Bundle Name".equals(b.getName());
            boolean statusIsDraft = b.getStatus() == ProductBundle.BundleStatus.DRAFT;
            // Check if code contains the original code or is generally populated
            boolean codeIsPopulated = b.getCode() != null && b.getCode().contains("SRC-01");

            return nameMatches && statusIsDraft && codeIsPopulated;
        }));

        // Verify the Link save
        // Note: Ensure your service calls bundleLinkRepository.save() or .saveAll()
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
        // Arrange
        ProductBundleRequest request = new ProductBundleRequest();
        request.setName("Bad Bundle");

        ProductBundleRequest.BundleItemRequest item1 = new ProductBundleRequest.BundleItemRequest();
        item1.setProductId(1L);
        item1.setMainAccount(true); // First Main

        ProductBundleRequest.BundleItemRequest item2 = new ProductBundleRequest.BundleItemRequest();
        item2.setProductId(2L);
        item2.setMainAccount(true); // Second Main (Illegal)

        request.setItems(List.of(item1, item2));

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bundleService.createBundle(request));

        assertEquals("A bundle can only have 1 Main Account item.", ex.getMessage());
        verify(bundleRepository, never()).save(any());
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

        assertTrue(ex.getMessage().contains("Source bundle has multiple Main Accounts"));
        verify(bundleRepository, never()).save(any());
    }
}
