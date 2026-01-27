package com.bankengine.catalog.service;

import com.bankengine.catalog.model.BundleProductLink;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.repository.BundleProductLinkRepository;
import com.bankengine.common.exception.ValidationException;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.CategoryConflictRule;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.test.config.BaseServiceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CatalogConstraintServiceTest extends BaseServiceTest {

    @Mock
    private BankConfigurationRepository bankConfigurationRepository;
    @Mock
    private BundleProductLinkRepository bundleProductLinkRepository;

    @InjectMocks
    private CatalogConstraintService constraintService;

    @Test
    @DisplayName("Should throw exception when product categories conflict based on bank config")
    void validateCategoryCompatibility_ShouldThrowException_WhenConflictExists() {
        CategoryConflictRule rule = new CategoryConflictRule("WEALTH", "RETAIL");
        BankConfiguration config = new BankConfiguration();
        config.setBankId(TEST_BANK_ID);
        config.setCategoryConflictRules(List.of(rule));

        when(bankConfigurationRepository.findByBankId(TEST_BANK_ID)).thenReturn(Optional.of(config));

        Product newProduct = new Product();
        newProduct.setCategory("RETAIL");

        Product existingProduct = new Product();
        existingProduct.setCategory("WEALTH");
        List<Product> existingProducts = List.of(existingProduct);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> constraintService.validateCategoryCompatibility(newProduct, existingProducts));

        assertTrue(ex.getMessage().contains("Conflict: Category 'RETAIL' cannot be bundled with category 'WEALTH'"));
        assertTrue(ex.getMessage().contains(TEST_BANK_ID));
    }

    @Test
    @DisplayName("Should pass when no conflict rules exist for the bank")
    void validateCategoryCompatibility_ShouldPass_WhenNoRulesDefined() {
        when(bankConfigurationRepository.findByBankId(TEST_BANK_ID)).thenReturn(Optional.empty());
        Product newProduct = new Product();
        newProduct.setCategory("WEALTH");
        List<Product> existingProducts = List.of(new Product());
        assertDoesNotThrow(() -> constraintService.validateCategoryCompatibility(newProduct, existingProducts));
    }

    @Test
    @DisplayName("Should pass when product is in an ARCHIVED bundle (Audit-Safe)")
    void validateProductCanBeBundled_ShouldPass_WhenLinkIsArchived() {
        Long productId = 100L;

        // Setup Mock: Bank prohibits multi-bundling
        BankConfiguration config = new BankConfiguration();
        config.setAllowProductInMultipleBundles(false);
        when(bankConfigurationRepository.findByBankId(TEST_BANK_ID)).thenReturn(Optional.of(config));

        // Setup Mock: Product has a link, but the bundle is ARCHIVED
        ProductBundle archivedBundle = new ProductBundle();
        archivedBundle.setStatus(ProductBundle.BundleStatus.ARCHIVED);

        BundleProductLink link = new BundleProductLink();
        link.setProductBundle(archivedBundle);

        // Use the NEW list-based method we created
        when(bundleProductLinkRepository.findAllByProductId(productId)).thenReturn(List.of(link));

        assertDoesNotThrow(() -> constraintService.validateProductCanBeBundled(productId));
    }

    @Test
    @DisplayName("Should throw exception when product is in an ACTIVE bundle")
    void validateProductCanBeBundled_ShouldFail_WhenLinkIsActive() {
        Long productId = 100L;

        BankConfiguration config = new BankConfiguration();
        config.setAllowProductInMultipleBundles(false);
        when(bankConfigurationRepository.findByBankId(TEST_BANK_ID)).thenReturn(Optional.of(config));

        ProductBundle activeBundle = new ProductBundle();
        activeBundle.setCode("BNDL-ACTIVE");
        activeBundle.setStatus(ProductBundle.BundleStatus.ACTIVE);

        BundleProductLink link = new BundleProductLink();
        link.setProductBundle(activeBundle);

        when(bundleProductLinkRepository.findAllByProductId(productId)).thenReturn(List.of(link));

        ValidationException ex = assertThrows(ValidationException.class,
                () -> constraintService.validateProductCanBeBundled(productId));

        assertTrue(ex.getMessage().contains("currently in an ACTIVE bundle"));
    }

    @Test
    @DisplayName("Should pass regardless of links if bank allows multi-bundling")
    void validateProductCanBeBundled_ShouldPass_WhenBankAllowsIt() {
        Long productId = 100L;

        BankConfiguration config = new BankConfiguration();
        config.setAllowProductInMultipleBundles(true); // Flag is ON
        when(bankConfigurationRepository.findByBankId(TEST_BANK_ID)).thenReturn(Optional.of(config));

        // Even if there are links, the method should return early
        assertDoesNotThrow(() -> constraintService.validateProductCanBeBundled(productId));
    }
}