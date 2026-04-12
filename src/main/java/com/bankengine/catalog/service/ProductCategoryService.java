package com.bankengine.catalog.service;

import com.bankengine.catalog.dto.ProductCategoryDto;
import com.bankengine.catalog.model.ProductCategory;
import com.bankengine.catalog.repository.ProductCategoryRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.common.service.BaseService;
import com.bankengine.common.util.CodeGeneratorUtil;
import com.bankengine.web.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductCategoryService extends BaseService {

    private final ProductCategoryRepository productCategoryRepository;
    private final ProductRepository productRepository;

    public ProductCategoryService(ProductCategoryRepository productCategoryRepository,
                                  ProductRepository productRepository) {
        this.productCategoryRepository = productCategoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductCategoryDto> listCategories() {
        String bankId = getCurrentBankId();
        return productCategoryRepository.findAllByBankIdOrderByArchivedThenName(bankId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ProductCategoryDto createCategory(ProductCategoryDto request) {
        String bankId = getCurrentBankId();
        String code = CodeGeneratorUtil.sanitizeAsCode(request.getCode());
        String name = request.getName() == null ? "" : request.getName().trim();

        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Category code is required.");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("Category name is required.");
        }

        productCategoryRepository.findByBankIdAndCode(bankId, code)
                .ifPresent(existing -> {
                    throw new IllegalStateException("Category code '" + code + "' already exists.");
                });

        ProductCategory saved = productCategoryRepository.save(ProductCategory.builder()
                .bankId(bankId)
                .code(code)
                .name(name)
                .archived(false)
                .build());

        return toDto(saved);
    }

    @Transactional
    public ProductCategoryDto archiveCategory(Long id) {
        ProductCategory category = getByIdSecurely(productCategoryRepository, id, "Product Category");

        String normalizedCode = category.getCode() == null ? "" : category.getCode().trim().toUpperCase();
        boolean linkedToProducts = productRepository.existsByBankIdAndNormalizedCategory(category.getBankId(), normalizedCode);
        if (linkedToProducts) {
            throw new ValidationException("Category '" + category.getCode()
                    + "' cannot be archived because it is linked to existing products.");
        }

        category.setArchived(true);
        return toDto(productCategoryRepository.save(category));
    }

    private ProductCategoryDto toDto(ProductCategory category) {
        return ProductCategoryDto.builder()
                .id(category.getId())
                .code(category.getCode())
                .name(category.getName())
                .archived(category.isArchived())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
