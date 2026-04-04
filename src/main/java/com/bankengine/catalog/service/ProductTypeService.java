package com.bankengine.catalog.service;

import com.bankengine.catalog.converter.ProductTypeMapper;
import com.bankengine.catalog.dto.ProductTypeDto;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.common.service.BaseService;
import com.bankengine.common.util.CodeGeneratorUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductTypeService extends BaseService {

    private final ProductTypeRepository productTypeRepository;
    private final ProductTypeMapper productTypeMapper;

    public ProductTypeService(ProductTypeRepository productTypeRepository, ProductTypeMapper productTypeMapper) {
        this.productTypeRepository = productTypeRepository;
        this.productTypeMapper = productTypeMapper;
    }

    /**
     * Retrieves all available ProductType entities.
     * @return A list of all ProductType entities.
     */
    @Transactional(readOnly = true)
    public List<ProductType> findAllProductTypes() {
        return productTypeRepository.findAll();
    }

    /**
     * Creates a new ProductType entity from a DTO.
     * @param requestDto The DTO containing the name of the new product type.
     * @return The saved ProductType entity.
     */
    @Transactional
    public ProductType createProductType(ProductTypeDto requestDto) {
        sanitizeRequest(requestDto);
        String bankId = getCurrentBankId();
        if (productTypeRepository.findByBankIdAndCode(bankId, requestDto.getCode()).isPresent()) {
            throw new IllegalStateException("Product Type code '" + requestDto.getCode() + "' already exists.");
        }
        ProductType productType = productTypeMapper.toEntity(requestDto);
        productType.setBankId(bankId);
        productType.setStatus(VersionableEntity.EntityStatus.DRAFT);
        return productTypeRepository.save(productType);
    }

    @Transactional
    public ProductType updateProductType(Long id, ProductTypeDto dto) {
        sanitizeRequest(dto);
        ProductType entity = getProductTypeById(id);
        if (entity.getStatus() != VersionableEntity.EntityStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT product types can be updated.");
        }
        if (dto.getCode() != null && !dto.getCode().equals(entity.getCode())) {
            productTypeRepository.findByBankIdAndCode(getCurrentBankId(), dto.getCode())
                    .filter(existing -> !existing.getId().equals(entity.getId()))
                    .ifPresent(existing -> {
                        throw new IllegalStateException("Product Type code '" + dto.getCode() + "' already exists.");
                    });
        }
        productTypeMapper.updateFromDto(dto, entity);
        return productTypeRepository.save(entity);
    }

    @Transactional
    public ProductType activateProductType(Long id) {
        ProductType entity = getProductTypeById(id);
        if (entity.getStatus() != VersionableEntity.EntityStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT product types can be activated.");
        }
        entity.setStatus(VersionableEntity.EntityStatus.ACTIVE);
        return productTypeRepository.save(entity);
    }

    @Transactional
    public ProductType archiveProductType(Long id) {
        ProductType entity = getProductTypeById(id);
        entity.setStatus(VersionableEntity.EntityStatus.ARCHIVED);
        return productTypeRepository.save(entity);
    }

    @Transactional
    public void deleteProductType(Long id) {
        ProductType entity = getProductTypeById(id);
        if (entity.getStatus() == VersionableEntity.EntityStatus.DRAFT) {
            productTypeRepository.delete(entity);
            return;
        }

        entity.setStatus(VersionableEntity.EntityStatus.ARCHIVED);
        productTypeRepository.save(entity);
    }

    private ProductType getProductTypeById(Long id) {
        return getByIdSecurely(productTypeRepository, id, "Product Type");
    }

    private void sanitizeRequest(ProductTypeDto dto) {
        if (dto.getCode() != null) {
            dto.setCode(CodeGeneratorUtil.sanitizeAsCode(dto.getCode()));
        }
    }
}
