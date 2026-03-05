package com.bankengine.catalog.service;

import com.bankengine.catalog.model.BundleProductLink;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.repository.BundleProductLinkRepository;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.CategoryConflictRule;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.test.config.BaseServiceTest;
import com.bankengine.web.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.bankengine.common.util.CodeGeneratorUtil.generateValidCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CatalogConstraintServiceTest extends BaseServiceTest {

    @Mock
    private BankConfigurationRepository bankConfigurationRepository;
    @Mock
    private BundleProductLinkRepository bundleProductLinkRepository;

    @InjectMocks
    private CatalogConstraintService constraintService;

    // --- CATEGORY COMPATIBILITY TESTS ---

    @Test
    @DisplayName("validateCategoryCompatibility: Should throw exact message when conflict exists")
    void validateCategoryCompatibility_Conflict_ThrowsExactMessage() {
        // Given
        setupBankConfig(false, List.of(new CategoryConflictRule("WEALTH", "RETAIL")));
        Product newProduct = createProduct("RETAIL", "Retail Loan");
        Product existingProduct = createProduct("WEALTH", "Wealth Investment");

        ValidationException ex = assertThrows(ValidationException.class,
                () -> constraintService.validateCategoryCompatibility(newProduct, List.of(existingProduct)));

        String expectedMessage = String.format(
                "Conflict: Category 'RETAIL' cannot be bundled with category 'WEALTH' for bank %s.",
                TEST_BANK_ID);
        assertEquals(expectedMessage, ex.getMessage());
    }

    @Test
    @DisplayName("validateCategoryCompatibility: Should pass when no rules are defined")
    void validateCategoryCompatibility_NoRules_Passes() {
        when(bankConfigurationRepository.findCurrent()).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> constraintService.validateCategoryCompatibility(
                createProduct("WEALTH", "Any"), List.of(createProduct("RETAIL", "Any"))));
    }

    // --- MULTI-BUNDLE VALIDATION TESTS ---

    @Test
    @DisplayName("validateProductCanBeBundled: Should throw exact message when product is in ACTIVE bundle")
    void validateProductCanBeBundled_ActiveLink_ThrowsExactMessage() {
        Product product = createProduct("RETAIL", "Savings product");
        product.setId(100l);
        setupBankConfig(false, List.of());
        ProductBundle activeBundle = createBundle(VersionableEntity.EntityStatus.ACTIVE);
        when(bundleProductLinkRepository.findAllByProductId(product.getId())).thenReturn(List.of(createLink(activeBundle, product.getId())));

        ValidationException exception = assertThrows(ValidationException.class,
                () -> constraintService.validateProductCanBeBundled(product.getId()));

        assertThat(exception.getMessage())
                .contains("[BUNDLE_CONSTRAINT_VIOLATION]")
                .contains("Bundling a product in more than 1 bundles is disabled")
                .contains("ID: 100");
    }

    @Test
    @DisplayName("validateProductCanBeBundled: Should pass when existing bundle is INACTIVE (Archived)")
    void validateProductCanBeBundled_InactiveLink_Passes() {
        Long productId = 100L;
        setupBankConfig(false, List.of());
        ProductBundle archivedBundle = createBundle(VersionableEntity.EntityStatus.INACTIVE);
        when(bundleProductLinkRepository.findAllByProductId(productId)).thenReturn(List.of(createLink(archivedBundle, productId)));
        assertDoesNotThrow(() -> constraintService.validateProductCanBeBundled(productId));
    }

    @Test
    @DisplayName("validateProductCanBeBundled: Should pass regardless of links when bank allows multi-bundling")
    void validateProductCanBeBundled_MultiBundleAllowed_Passes() {
        setupBankConfig(true, List.of());
        assertDoesNotThrow(() -> constraintService.validateProductCanBeBundled(999L));
    }

    @Test
    @DisplayName("Multi-Bundle: Should FAIL when product is in DRAFT bundle and multi-bundle is OFF")
    void validateProductCanBeBundled_DraftLink_MultiBundleOff_ThrowsException() {
        Product product = createProduct("RETAIL", "Savings product");
        product.setId(100L);
        setupBankConfig(false, List.of());

        ProductBundle draftBundle = createBundle(VersionableEntity.EntityStatus.DRAFT);
        when(bundleProductLinkRepository.findAllByProductId(product.getId()))
                .thenReturn(List.of(createLink(draftBundle, product.getId())));
        ValidationException ex = assertThrows(ValidationException.class,
                () -> constraintService.validateProductCanBeBundled(product.getId()));

        assertThat(ex.getMessage())
                .contains("[BUNDLE_CONSTRAINT_VIOLATION]")
                .contains("DRAFT")
                .contains(draftBundle.getCode())
                .contains(TEST_BANK_ID);
    }

    @Test
    @DisplayName("Multi-Bundle: Should PASS when product is in ACTIVE bundle but multi-bundle is ON")
    void validateProductCanBeBundled_ActiveLink_MultiBundleOn_Passes() {
        setupBankConfig(true, List.of());
        assertDoesNotThrow(() -> constraintService.validateProductCanBeBundled(100L));
        verifyNoInteractions(bundleProductLinkRepository);
    }

    @Test
    @DisplayName("Multi-Bundle: Should PASS when bundle is ARCHIVED even if multi-bundle is OFF")
    void validateProductCanBeBundled_ArchivedLink_MultiBundleOff_Passes() {
        Long productId = 100L;
        setupBankConfig(false, List.of());

        ProductBundle archivedBundle = createBundle(VersionableEntity.EntityStatus.ARCHIVED);
        when(bundleProductLinkRepository.findAllByProductId(productId)).thenReturn(List.of(createLink(archivedBundle, productId)));
        assertDoesNotThrow(() -> constraintService.validateProductCanBeBundled(productId));
    }

    // --- HELPERS ---

    private void setupBankConfig(boolean allowMulti, List<CategoryConflictRule> rules) {
        BankConfiguration config = new BankConfiguration();
        config.setBankId(TEST_BANK_ID);
        config.setAllowProductInMultipleBundles(allowMulti);
        config.setCategoryConflictRules(rules);
        when(bankConfigurationRepository.findCurrent()).thenReturn(Optional.of(config));
    }

    private Product createProduct(String category, String name) {
        Product product = new Product();
        product.setCategory(category);
        product.setName(name);
        product.setCode(generateValidCode(name));
        return product;
    }

    private ProductBundle createBundle(VersionableEntity.EntityStatus status) {
        ProductBundle productBundle = new ProductBundle();
        productBundle.setCode(generateValidCode(null));
        productBundle.setStatus(status);
        return productBundle;
    }

    private BundleProductLink createLink(ProductBundle bundle, Long productId) {
        BundleProductLink link = new BundleProductLink();
        link.setProductBundle(bundle);

        String name = "Test Product " + productId;
        Product dummyProduct = new Product();
        dummyProduct.setId(productId);
        dummyProduct.setCode(generateValidCode(name));
        dummyProduct.setName(name);

        link.setProduct(dummyProduct);
        return link;
    }
}