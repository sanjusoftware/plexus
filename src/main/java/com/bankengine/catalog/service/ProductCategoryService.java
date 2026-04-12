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
