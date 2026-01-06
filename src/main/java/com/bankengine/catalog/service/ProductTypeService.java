package com.bankengine.catalog.service;

import com.bankengine.auth.security.BankContextHolder;
import com.bankengine.catalog.converter.ProductTypeMapper;
import com.bankengine.catalog.dto.ProductTypeDto;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductTypeService {

    private final ProductTypeRepository productTypeRepository;
    private final ProductTypeMapper productTypeMapper;

    public ProductTypeService(ProductTypeRepository productTypeRepository, ProductTypeMapper productTypeMapper) {
        this.productTypeRepository = productTypeRepository;
        this.productTypeMapper = productTypeMapper;
    }

    /**
     * Retrieves all available ProductType entities.
     * This is useful for populating dropdowns or product creation forms.
     * @return A list of all ProductType entities.
     */
    @Transactional(readOnly = true)
    public List<ProductType> findAllProductTypes() {
        // Simple retrieval of all product types.
        return productTypeRepository.findAll();
    }

    /**
     * Creates a new ProductType entity from a DTO.
     * @param requestDto The DTO containing the name of the new product type.
     * @return The saved ProductType entity.
     */
    @Transactional
    public ProductType createProductType(ProductTypeDto requestDto) {
        String bankId = BankContextHolder.getBankId();

        ProductType productType = productTypeMapper.toEntity(requestDto);
        productType.setBankId(bankId);

        return productTypeRepository.save(productType);
    }
}