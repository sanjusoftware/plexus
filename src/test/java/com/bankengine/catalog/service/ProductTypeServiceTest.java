package com.bankengine.catalog.service;

import com.bankengine.catalog.converter.ProductTypeMapper;
import com.bankengine.catalog.dto.ProductTypeDto;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.test.config.BaseServiceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductTypeServiceTest extends BaseServiceTest {
    @Mock
    private ProductTypeRepository productTypeRepository;
    @Mock
    private ProductTypeMapper productTypeMapper;
    @InjectMocks
    private ProductTypeService productTypeService;

    @Test
    @DisplayName("Find All - Should return all product types from repository")
    void testFindAllProductTypes() {
        when(productTypeRepository.findAll()).thenReturn(Collections.singletonList(new ProductType()));
        List<ProductType> result = productTypeService.findAllProductTypes();
        assertEquals(1, result.size());
        verify(productTypeRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Create Product Type - Should use bankId from context and save entity")
    void testCreateProductType() {
        ProductTypeDto dto = new ProductTypeDto();
        dto.setName("LOAN");
        dto.setCode("LOAN_CODE");

        when(productTypeRepository.findByBankIdAndCode(any(), any())).thenReturn(Optional.empty());
        when(productTypeMapper.toEntity(dto)).thenReturn(new ProductType());
        when(productTypeRepository.save(argThat(entity ->
                TEST_BANK_ID.equals(entity.getBankId())
        ))).thenReturn(new ProductType());

        ProductType result = productTypeService.createProductType(dto);
        assertNotNull(result);
        verify(productTypeRepository, times(1)).save(any(ProductType.class));
    }

    @Test
    void createProductType_Duplicate_ThrowsException() {
        ProductTypeDto dto = new ProductTypeDto();
        dto.setCode("EXISTING");
        when(productTypeRepository.findByBankIdAndCode(any(), eq("EXISTING"))).thenReturn(Optional.of(new ProductType()));

        assertThrows(IllegalStateException.class, () -> productTypeService.createProductType(dto));
    }

    @Test
    void updateProductType_Success() {
        ProductType existing = new ProductType();
        existing.setStatus(VersionableEntity.EntityStatus.DRAFT);
        existing.setBankId(TEST_BANK_ID);
        when(productTypeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productTypeRepository.save(existing)).thenReturn(existing);

        ProductTypeDto dto = new ProductTypeDto();
        dto.setName("New Name");
        productTypeService.updateProductType(1L, dto);

        verify(productTypeMapper).updateFromDto(dto, existing);
    }

    @Test
    void updateProductType_NotDraft_ThrowsException() {
        ProductType existing = new ProductType();
        existing.setStatus(VersionableEntity.EntityStatus.ACTIVE);
        existing.setBankId(TEST_BANK_ID);
        when(productTypeRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class, () -> productTypeService.updateProductType(1L, new ProductTypeDto()));
    }

    @Test
    void activateProductType_Success() {
        ProductType existing = new ProductType();
        existing.setStatus(VersionableEntity.EntityStatus.DRAFT);
        existing.setBankId(TEST_BANK_ID);
        when(productTypeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productTypeRepository.save(existing)).thenReturn(existing);

        productTypeService.activateProductType(1L);
        assertEquals(VersionableEntity.EntityStatus.ACTIVE, existing.getStatus());
    }

    @Test
    void archiveProductType_Success() {
        ProductType existing = new ProductType();
        existing.setBankId(TEST_BANK_ID);
        when(productTypeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productTypeRepository.save(existing)).thenReturn(existing);

        productTypeService.archiveProductType(1L);
        assertEquals(VersionableEntity.EntityStatus.ARCHIVED, existing.getStatus());
    }

    @Test
    void deleteProductType_Success() {
        ProductType existing = new ProductType();
        existing.setStatus(VersionableEntity.EntityStatus.DRAFT);
        existing.setBankId(TEST_BANK_ID);
        when(productTypeRepository.findById(1L)).thenReturn(Optional.of(existing));

        productTypeService.deleteProductType(1L);
        verify(productTypeRepository).delete(existing);
    }

    @Test
    void deleteProductType_NotDraft_ThrowsException() {
        ProductType existing = new ProductType();
        existing.setStatus(VersionableEntity.EntityStatus.ACTIVE);
        existing.setBankId(TEST_BANK_ID);
        when(productTypeRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class, () -> productTypeService.deleteProductType(1L));
    }
}
