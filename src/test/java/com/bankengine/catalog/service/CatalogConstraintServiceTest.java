package com.bankengine.catalog.service;

import com.bankengine.catalog.model.Product;
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
}