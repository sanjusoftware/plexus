package com.bankengine.catalog.service;

import com.bankengine.auth.security.BankContextHolder;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.repository.BundleProductLinkRepository;
import com.bankengine.common.exception.ValidationException;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.CategoryConflictRule;
import com.bankengine.common.repository.BankConfigurationRepository;
import org.junit.jupiter.api.AfterEach;
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
public class CatalogConstraintServiceTest {
    @Mock
    private BankConfigurationRepository bankConfigurationRepository;

    @Mock
    private BundleProductLinkRepository bundleProductLinkRepository;

    @InjectMocks
    private CatalogConstraintService constraintService;

    @AfterEach
    void tearDown() {
        BankContextHolder.clear();
    }

    @Test
    @DisplayName("Should throw exception when product categories conflict based on bank config")
    void validateCategoryCompatibility_ShouldThrowException_WhenConflictExists() {
        // Arrange
        String bankId = "TEST_BANK";
        BankContextHolder.setBankId(bankId);

        CategoryConflictRule rule = new CategoryConflictRule("WEALTH", "RETAIL");
        BankConfiguration config = new BankConfiguration();
        config.setBankId(bankId);
        config.setCategoryConflictRules(List.of(rule));

        when(bankConfigurationRepository.findByBankId(bankId)).thenReturn(Optional.of(config));

        Product newProduct = new Product();
        newProduct.setCategory("RETAIL");

        Product existingProduct = new Product();
        existingProduct.setCategory("WEALTH");
        List<Product> existingProducts = List.of(existingProduct);

        // Act & Assert
        ValidationException ex = assertThrows(ValidationException.class,
                () -> constraintService.validateCategoryCompatibility(newProduct, existingProducts));

        assertEquals("Conflict: Category 'RETAIL' cannot be bundled with category 'WEALTH' for this bank.", ex.getMessage());
    }

    @Test
    @DisplayName("Should pass when no conflict rules exist for the bank")
    void validateCategoryCompatibility_ShouldPass_WhenNoRulesDefined() {
        // Arrange
        String bankId = "TEST_BANK";
        BankContextHolder.setBankId(bankId);
        when(bankConfigurationRepository.findByBankId(bankId)).thenReturn(Optional.empty());

        Product newProduct = new Product();
        newProduct.setCategory("WEALTH");
        List<Product> existingProducts = List.of(new Product()); // Category doesn't matter

        // Act & Assert
        assertDoesNotThrow(() -> constraintService.validateCategoryCompatibility(newProduct, existingProducts));
    }
}
