package com.bankengine.catalog.service;

import com.bankengine.catalog.dto.ProductCategoryDto;
import com.bankengine.catalog.model.ProductCategory;
import com.bankengine.catalog.repository.ProductCategoryRepository;
import com.bankengine.test.config.BaseServiceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCategoryServiceTest extends BaseServiceTest {

    @Mock
    private ProductCategoryRepository productCategoryRepository;

    @InjectMocks
    private ProductCategoryService productCategoryService;

    @Test
    @DisplayName("CreateCategory should sanitize code and persist")
    void createCategory_ShouldSanitizeAndPersist() {
        ProductCategoryDto request = ProductCategoryDto.builder().code("retail segment").name("Retail Segment").build();

        when(productCategoryRepository.findByBankIdAndCode(TEST_BANK_ID, "RETAIL_SEGMENT")).thenReturn(Optional.empty());
        when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductCategoryDto saved = productCategoryService.createCategory(request);

        assertEquals("RETAIL_SEGMENT", saved.getCode());
        assertEquals("Retail Segment", saved.getName());
        verify(productCategoryRepository).save(any(ProductCategory.class));
    }

    @Test
    @DisplayName("CreateCategory should reject duplicate bank scoped code")
    void createCategory_WhenDuplicate_ShouldThrow() {
        ProductCategory existing = ProductCategory.builder().bankId(TEST_BANK_ID).code("RETAIL").name("Retail").build();
        when(productCategoryRepository.findByBankIdAndCode(TEST_BANK_ID, "RETAIL")).thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class,
                () -> productCategoryService.createCategory(ProductCategoryDto.builder().code("retail").name("Retail").build()));
        verify(productCategoryRepository, never()).save(any(ProductCategory.class));
    }

    @Test
    @DisplayName("ListCategories should return sorted tenant categories")
    void listCategories_ShouldReturnRows() {
        when(productCategoryRepository.findAllByBankIdOrderByName(TEST_BANK_ID)).thenReturn(List.of(
                ProductCategory.builder().id(1L).bankId(TEST_BANK_ID).code("CORPORATE").name("Corporate").build(),
                ProductCategory.builder().id(2L).bankId(TEST_BANK_ID).code("RETAIL").name("Retail").build()
        ));

        List<ProductCategoryDto> categories = productCategoryService.listCategories();
        assertEquals(2, categories.size());
        assertEquals("CORPORATE", categories.get(0).getCode());
    }

    @Test
    @DisplayName("UpdateCategory should update name")
    void updateCategory_ShouldUpdateName() {
        ProductCategory existing = ProductCategory.builder().id(1L).bankId(TEST_BANK_ID).code("RETAIL").name("Retail").build();
        when(productCategoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductCategoryDto request = ProductCategoryDto.builder().name("Retail Updated").build();
        ProductCategoryDto updated = productCategoryService.updateCategory(1L, request);

        assertEquals("Retail Updated", updated.getName());
        assertEquals("RETAIL", updated.getCode());
    }

    @Test
    @DisplayName("DeleteCategory should delete if not in use")
    void deleteCategory_ShouldDelete() {
        ProductCategory existing = ProductCategory.builder().id(1L).bankId(TEST_BANK_ID).code("RETAIL").name("Retail").build();
        when(productCategoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productCategoryRepository.isCategoryInUse(TEST_BANK_ID, "RETAIL")).thenReturn(false);

        productCategoryService.deleteCategory(1L);

        verify(productCategoryRepository).delete(existing);
    }

    @Test
    @DisplayName("DeleteCategory should throw if in use")
    void deleteCategory_InUse_ShouldThrow() {
        ProductCategory existing = ProductCategory.builder().id(1L).bankId(TEST_BANK_ID).code("RETAIL").name("Retail").build();
        when(productCategoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productCategoryRepository.isCategoryInUse(TEST_BANK_ID, "RETAIL")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> productCategoryService.deleteCategory(1L));
        verify(productCategoryRepository, never()).delete(any(ProductCategory.class));
    }
}

