package com.bankengine.catalog.controller;

import com.bankengine.catalog.dto.ProductFeatureDto;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.bankengine.catalog.dto.CreateProductRequestDto;
import com.bankengine.catalog.dto.ProductResponseDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    // Constructor Injection
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * POST /api/v1/products
     * Creates a new core Product entry in the catalog using DTO and validation.
     */
    @PostMapping
    public ResponseEntity<ProductResponseDto> createProduct(@Valid @RequestBody CreateProductRequestDto requestDto) {
        try {
            // Service method is updated to accept the DTO
            ProductResponseDto responseDto = productService.createProduct(requestDto);
            return new ResponseEntity<>(responseDto, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            // Handles the business validation error from the service layer
            return new ResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * GET /api/v1/products/{id}
     * Retrieves a Product and its associated features, returning a DTO.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDto> getProductById(@PathVariable Long id) {
        // Service method is updated to return DTO
        ProductResponseDto responseDto = productService.getProductResponseById(id);

        // Uses a helper method in the service to handle the Optional lookup
        return new ResponseEntity<>(responseDto, HttpStatus.OK);
    }

    /**
     * POST /api/v1/products/link-feature
     * Links a defined FeatureComponent to a specific Product instance.
     */
    @PostMapping("/link-feature")
    public ResponseEntity<ProductFeatureLink> linkFeatureToProduct(@RequestBody ProductFeatureDto dto) {
        try {
            ProductFeatureLink link = productService.linkFeatureToProduct(dto);
            return new ResponseEntity<>(link, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}