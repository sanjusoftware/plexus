package com.bankengine.catalog.service;

import com.bankengine.catalog.dto.ProductCategoryDto;
import com.bankengine.catalog.model.ProductCategory;
import com.bankengine.catalog.repository.ProductCategoryRepository;
import com.bankengine.common.service.BaseService;
import com.bankengine.common.util.CodeGeneratorUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductCategoryService extends BaseService {

    private final ProductCategoryRepository productCategoryRepository;

    public ProductCategoryService(ProductCategoryRepository productCategoryRepository) {
        this.productCategoryRepository = productCategoryRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductCategoryDto> listCategories() {
        String bankId = getCurrentBankId();
        return productCategoryRepository.findAllByBankIdOrderByName(bankId).stream()
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
                .build());

        return toDto(saved);
    }

    @Transactional
    public ProductCategoryDto updateCategory(Long id, ProductCategoryDto request) {
        String bankId = getCurrentBankId();
        ProductCategory category = productCategoryRepository.findById(id)
                .filter(c -> c.getBankId().equals(bankId))
                .orElseThrow(() -> new IllegalArgumentException("Product category not found."));

        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("Category name is required.");
        }

        category.setName(name);
        // Code is usually immutable once created as it's used as a reference in other tables
        return toDto(productCategoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long id) {
        String bankId = getCurrentBankId();
        ProductCategory category = productCategoryRepository.findById(id)
                .filter(c -> c.getBankId().equals(bankId))
                .orElseThrow(() -> new IllegalArgumentException("Product category not found."));

        // Check if category is in use by any product
        // Note: Product.category stores the CODE, not the ID.
        boolean inUse = productCategoryRepository.isCategoryInUse(bankId, category.getCode());
        if (inUse) {
            throw new IllegalStateException("Cannot delete category as it is currently in use by products.");
        }

        productCategoryRepository.delete(category);
    }

    private ProductCategoryDto toDto(ProductCategory category) {
        return ProductCategoryDto.builder()
                .id(category.getId())
                .code(category.getCode())
                .name(category.getName())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
