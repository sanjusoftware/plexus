package com.bankengine.catalog.controller;

import com.bankengine.catalog.dto.ProductCategoryDto;
import com.bankengine.catalog.service.ProductCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Product Category Master", description = "Manage bank-defined product category master data.")
@RestController
@RequestMapping("/api/v1/product-categories")
public class ProductCategoryController {

    private final ProductCategoryService productCategoryService;

    public ProductCategoryController(ProductCategoryService productCategoryService) {
        this.productCategoryService = productCategoryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('catalog:product:read')")
    @Operation(summary = "List product categories")
    public ResponseEntity<List<ProductCategoryDto>> listCategories() {
        return ResponseEntity.ok(productCategoryService.listCategories());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('catalog:product:create') or hasAuthority('catalog:product:update')")
    @Operation(summary = "Create product category")
    public ResponseEntity<ProductCategoryDto> createCategory(@Valid @RequestBody ProductCategoryDto request) {
        return new ResponseEntity<>(productCategoryService.createCategory(request), HttpStatus.CREATED);
    }
}
