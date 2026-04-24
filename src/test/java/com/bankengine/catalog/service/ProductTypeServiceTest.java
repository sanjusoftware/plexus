package com.bankengine.catalog.service;

import com.bankengine.catalog.converter.ProductTypeMapper;
import com.bankengine.catalog.dto.ProductTypeDto;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.common.model.VersionableEntity;
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
class ProductTypeServiceTest extends BaseServiceTest {

    @Mock private ProductTypeRepository repository;
    @Mock private ProductTypeMapper mapper;

    @InjectMocks
    private ProductTypeService service;

    @Test
    void testFindAllProductTypes() {
        when(repository.findAll()).thenReturn(List.of(new ProductType()));
        assertFalse(service.findAllProductTypes().isEmpty());
    }

    @Test
    void testCreateProductType_Success() {
        ProductTypeDto req = new ProductTypeDto(); req.setCode("TYPE");
        when(repository.findByBankIdAndCode(any(), eq("TYPE"))).thenReturn(Optional.empty());
        when(mapper.toEntity(req)).thenReturn(new ProductType());
        when(repository.save(any())).thenReturn(new ProductType());
        service.createProductType(req);
        verify(repository).save(any());
    }

    @Test
    void testCreateProductType_Duplicate() {
        ProductTypeDto req = new ProductTypeDto(); req.setCode("TYPE");
        when(repository.findByBankIdAndCode(any(), eq("TYPE"))).thenReturn(Optional.of(new ProductType()));
        assertThrows(IllegalStateException.class, () -> service.createProductType(req));
    }

    @Test
    void testUpdateProductType_Success() {
        ProductType pt = new ProductType(); pt.setId(1L); pt.setBankId(TEST_BANK_ID); pt.setStatus(VersionableEntity.EntityStatus.DRAFT); pt.setCode("OLD");
        ProductTypeDto dto = new ProductTypeDto(); dto.setCode("NEW");
        when(repository.findById(1L)).thenReturn(Optional.of(pt));
        when(repository.findByBankIdAndCode(any(), eq("NEW"))).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(pt);
        service.updateProductType(1L, dto);
        verify(repository).save(pt);
    }

    @Test
    void testUpdateProductType_NotDraft() {
        ProductType pt = new ProductType(); pt.setId(1L); pt.setBankId(TEST_BANK_ID); pt.setStatus(VersionableEntity.EntityStatus.ACTIVE);
        when(repository.findById(1L)).thenReturn(Optional.of(pt));
        assertThrows(IllegalStateException.class, () -> service.updateProductType(1L, new ProductTypeDto()));
    }

    @Test
    void testUpdateProductType_DuplicateCode() {
        ProductType pt = new ProductType(); pt.setId(1L); pt.setBankId(TEST_BANK_ID); pt.setStatus(VersionableEntity.EntityStatus.DRAFT); pt.setCode("OLD");
        ProductTypeDto dto = new ProductTypeDto(); dto.setCode("NEW");
        ProductType other = new ProductType(); other.setId(2L);
        when(repository.findById(1L)).thenReturn(Optional.of(pt));
        when(repository.findByBankIdAndCode(any(), eq("NEW"))).thenReturn(Optional.of(other));
        assertThrows(IllegalStateException.class, () -> service.updateProductType(1L, dto));
    }

    @Test
    void testUpdateProductType_SameCode() {
        ProductType pt = new ProductType(); pt.setId(1L); pt.setBankId(TEST_BANK_ID); pt.setStatus(VersionableEntity.EntityStatus.DRAFT); pt.setCode("SAME");
        ProductTypeDto dto = new ProductTypeDto(); dto.setCode("SAME");
        when(repository.findById(1L)).thenReturn(Optional.of(pt));
        when(repository.save(any())).thenReturn(pt);
        service.updateProductType(1L, dto);
        verify(repository, never()).findByBankIdAndCode(any(), any());
        verify(repository).save(pt);
    }

    @Test
    void testActivateProductType_Success() {
        ProductType pt = new ProductType(); pt.setId(1L); pt.setBankId(TEST_BANK_ID); pt.setStatus(VersionableEntity.EntityStatus.DRAFT);
        when(repository.findById(1L)).thenReturn(Optional.of(pt));
        when(repository.save(any())).thenReturn(pt);
        service.activateProductType(1L);
        assertEquals(VersionableEntity.EntityStatus.ACTIVE, pt.getStatus());
    }

    @Test
    void testActivateProductType_NotDraft() {
        ProductType pt = new ProductType(); pt.setId(1L); pt.setBankId(TEST_BANK_ID); pt.setStatus(VersionableEntity.EntityStatus.ACTIVE);
        when(repository.findById(1L)).thenReturn(Optional.of(pt));
        assertThrows(IllegalStateException.class, () -> service.activateProductType(1L));
    }

    @Test
    void testArchiveProductType() {
        ProductType pt = new ProductType(); pt.setId(1L); pt.setBankId(TEST_BANK_ID); pt.setStatus(VersionableEntity.EntityStatus.ACTIVE);
        when(repository.findById(1L)).thenReturn(Optional.of(pt));
        when(repository.save(any())).thenReturn(pt);
        service.archiveProductType(1L);
        assertEquals(VersionableEntity.EntityStatus.ARCHIVED, pt.getStatus());
    }

    @Test
    void testDeleteProductType_Draft() {
        ProductType pt = new ProductType(); pt.setId(1L); pt.setBankId(TEST_BANK_ID); pt.setStatus(VersionableEntity.EntityStatus.DRAFT);
        when(repository.findById(1L)).thenReturn(Optional.of(pt));
        service.deleteProductType(1L);
        verify(repository).delete(pt);
    }

    @Test
    void testDeleteProductType_NonDraft() {
        ProductType pt = new ProductType(); pt.setId(1L); pt.setBankId(TEST_BANK_ID); pt.setStatus(VersionableEntity.EntityStatus.ACTIVE);
        when(repository.findById(1L)).thenReturn(Optional.of(pt));
        when(repository.save(any())).thenReturn(pt);
        service.deleteProductType(1L);
        assertEquals(VersionableEntity.EntityStatus.ARCHIVED, pt.getStatus());
        verify(repository).save(pt);
    }

    @Test
    void testSanitizeRequest_NullCode() {
        ProductTypeDto dto = new ProductTypeDto();
        dto.setCode(null);
        when(repository.findById(1L)).thenReturn(Optional.of(new ProductType() {{ setId(1L); setBankId(TEST_BANK_ID); setStatus(VersionableEntity.EntityStatus.DRAFT); }}));
        when(repository.save(any())).thenReturn(new ProductType());
        service.updateProductType(1L, dto);
        assertNull(dto.getCode());
    }
}
