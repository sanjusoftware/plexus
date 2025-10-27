package com.bankengine.catalog.service;

import com.bankengine.catalog.dto.CreateProductTypeRequestDto;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductTypeService {

    private final ProductTypeRepository productTypeRepository;

    public ProductTypeService(ProductTypeRepository productTypeRepository) {
        this.productTypeRepository = productTypeRepository;
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
    public ProductType createProductType(CreateProductTypeRequestDto requestDto) {
        // NOTE: Add logic here to check for duplicate names if needed

        ProductType productType = new ProductType();
        productType.setName(requestDto.getName());

        return productTypeRepository.save(productType);
    }
}