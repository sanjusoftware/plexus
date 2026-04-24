package com.bankengine.catalog.service;

import com.bankengine.catalog.dto.ProductCategoryDto;
import com.bankengine.catalog.model.ProductCategory;
import com.bankengine.catalog.repository.ProductCategoryRepository;
import com.bankengine.test.config.BaseServiceTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCategoryServiceTest extends BaseServiceTest {

    @Mock
    private ProductCategoryRepository repository;

    @InjectMocks
    private ProductCategoryService service;

    @Test
    void testListCategories() {
        when(repository.findAllByBankIdOrderByName(TEST_BANK_ID)).thenReturn(List.of(
            ProductCategory.builder().id(1L).code("C1").name("N1").build()
        ));
        List<ProductCategoryDto> result = service.listCategories();
        assertFalse(result.isEmpty());
        assertEquals("C1", result.get(0).getCode());
    }

    @Test
    void testCreateCategory_Success() {
        ProductCategoryDto req = ProductCategoryDto.builder().code("NEW").name("New Name").build();
        when(repository.findByBankIdAndCode(TEST_BANK_ID, "NEW")).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(ProductCategory.builder().code("NEW").name("New Name").build());

        ProductCategoryDto result = service.createCategory(req);
        assertEquals("NEW", result.getCode());
    }

    @Test
    void testCreateCategory_MissingCode() {
        ProductCategoryDto req = ProductCategoryDto.builder().code("").name("Name").build();
        assertThrows(IllegalArgumentException.class, () -> service.createCategory(req));
    }

    @Test
    void testCreateCategory_MissingName() {
        ProductCategoryDto req = ProductCategoryDto.builder().code("C").name("").build();
        assertThrows(IllegalArgumentException.class, () -> service.createCategory(req));
    }

    @Test
    void testCreateCategory_NullName() {
        ProductCategoryDto req = ProductCategoryDto.builder().code("C").name(null).build();
        assertThrows(IllegalArgumentException.class, () -> service.createCategory(req));
    }

    @Test
    void testCreateCategory_Duplicate() {
        ProductCategoryDto req = ProductCategoryDto.builder().code("EXIST").name("Name").build();
        when(repository.findByBankIdAndCode(TEST_BANK_ID, "EXIST")).thenReturn(Optional.of(new ProductCategory()));
        assertThrows(IllegalStateException.class, () -> service.createCategory(req));
    }

    @Test
    void testUpdateCategory_Success() {
        ProductCategory cat = ProductCategory.builder().id(1L).bankId(TEST_BANK_ID).code("C").name("Old").build();
        when(repository.findById(1L)).thenReturn(Optional.of(cat));
        when(repository.save(any())).thenReturn(cat);

        ProductCategoryDto req = ProductCategoryDto.builder().name("New").build();
        ProductCategoryDto result = service.updateCategory(1L, req);
        assertEquals("New", result.getName());
    }

    @Test
    void testUpdateCategory_NotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.updateCategory(1L, ProductCategoryDto.builder().name("N").build()));
    }

    @Test
    void testUpdateCategory_WrongBank() {
        ProductCategory cat = ProductCategory.builder().id(1L).bankId("OTHER").code("C").name("Old").build();
        when(repository.findById(1L)).thenReturn(Optional.of(cat));
        assertThrows(IllegalArgumentException.class, () -> service.updateCategory(1L, ProductCategoryDto.builder().name("N").build()));
    }

    @Test
    void testUpdateCategory_MissingName() {
        ProductCategory cat = ProductCategory.builder().id(1L).bankId(TEST_BANK_ID).code("C").name("Old").build();
        when(repository.findById(1L)).thenReturn(Optional.of(cat));
        assertThrows(IllegalArgumentException.class, () -> service.updateCategory(1L, ProductCategoryDto.builder().name("").build()));
    }

    @Test
    void testDeleteCategory_Success() {
        ProductCategory cat = ProductCategory.builder().id(1L).bankId(TEST_BANK_ID).code("C").build();
        when(repository.findById(1L)).thenReturn(Optional.of(cat));
        when(repository.isCategoryInUse(TEST_BANK_ID, "C")).thenReturn(false);

        service.deleteCategory(1L);
        verify(repository).delete(cat);
    }

    @Test
    void testDeleteCategory_InUse() {
        ProductCategory cat = ProductCategory.builder().id(1L).bankId(TEST_BANK_ID).code("C").build();
        when(repository.findById(1L)).thenReturn(Optional.of(cat));
        when(repository.isCategoryInUse(TEST_BANK_ID, "C")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.deleteCategory(1L));
    }
}
