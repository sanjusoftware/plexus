package com.bankengine.catalog.service;

import com.bankengine.auth.security.BankContextHolder;
import com.bankengine.catalog.converter.ProductTypeMapper;
import com.bankengine.catalog.dto.ProductTypeDto;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductTypeServiceTest {

    @Mock
    private ProductTypeRepository productTypeRepository;

    @Mock
    private ProductTypeMapper productTypeMapper;

    @InjectMocks
    private ProductTypeService productTypeService;

    @Test
    void testFindAllProductTypes() {
        when(productTypeRepository.findAll()).thenReturn(Collections.singletonList(new ProductType()));

        List<ProductType> result = productTypeService.findAllProductTypes();

        assertEquals(1, result.size());
        verify(productTypeRepository, times(1)).findAll();
    }

    @Test
    void testCreateProductType() {
        final String TEST_BANK_ID = "BANK_A_UNIT_TEST";
        BankContextHolder.setBankId(TEST_BANK_ID);

        try {
            ProductTypeDto dto = new ProductTypeDto();
            when(productTypeMapper.toEntity(dto)).thenReturn(new ProductType());
            when(productTypeRepository.save(any(ProductType.class))).thenReturn(new ProductType());

            ProductType result = productTypeService.createProductType(dto);

            assertNotNull(result);
            verify(productTypeRepository, times(1)).save(any(ProductType.class));
        } finally {
            BankContextHolder.clear();
        }
    }
}
