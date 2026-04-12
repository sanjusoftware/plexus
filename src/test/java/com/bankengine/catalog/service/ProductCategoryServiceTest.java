package com.bankengine.catalog.service;

import com.bankengine.catalog.model.ProductCategory;
import com.bankengine.catalog.repository.ProductCategoryRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.test.config.BaseServiceTest;
import com.bankengine.web.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCategoryServiceTest extends BaseServiceTest {

    @Mock
    private ProductCategoryRepository productCategoryRepository;
    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductCategoryService productCategoryService;

    @Test
    @DisplayName("ArchiveCategory should hard-block when linked products exist")
    void archiveCategory_WhenLinkedProductsExist_ShouldThrowValidationException() {
        ProductCategory category = ProductCategory.builder()
                .id(101L)
                .bankId(TEST_BANK_ID)
                .code(" retail ")
                .name("Retail")
                .archived(false)
                .build();

        when(productCategoryRepository.findById(101L)).thenReturn(Optional.of(category));
        when(productRepository.existsByBankIdAndNormalizedCategory(TEST_BANK_ID, "RETAIL")).thenReturn(true);

        assertThrows(ValidationException.class, () -> productCategoryService.archiveCategory(101L));
        verify(productCategoryRepository, never()).save(category);
    }

    @Test
    @DisplayName("ArchiveCategory should archive when no linked products exist")
    void archiveCategory_WhenNoLinkedProducts_ShouldArchive() {
        ProductCategory category = ProductCategory.builder()
                .id(102L)
                .bankId(TEST_BANK_ID)
                .code("RETAIL")
                .name("Retail")
                .archived(false)
                .build();

        when(productCategoryRepository.findById(102L)).thenReturn(Optional.of(category));
        when(productRepository.existsByBankIdAndNormalizedCategory(TEST_BANK_ID, "RETAIL")).thenReturn(false);
        when(productCategoryRepository.save(category)).thenReturn(category);

        var response = productCategoryService.archiveCategory(102L);

        assertTrue(category.isArchived());
        assertEquals("RETAIL", response.getCode());
        verify(productRepository).existsByBankIdAndNormalizedCategory(eq(TEST_BANK_ID), eq("RETAIL"));
    }
}

