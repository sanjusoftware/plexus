package com.bankengine.catalog.controller;

import com.bankengine.catalog.dto.ProductFeatureDto;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
     * Creates a new core Product entry in the catalog.
     */
    @PostMapping
    public ResponseEntity<Product> createProduct(@RequestBody Product product) {
        try {
            Product createdProduct = productService.createProduct(product);
            // Returns the created product with a 201 Created status
            return new ResponseEntity<>(createdProduct, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            // Handles the business validation error from the service layer
            return new ResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * GET /api/v1/products/{id}
     * Retrieves a Product and its associated features.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        Optional<Product> product = productService.getProductById(id);

        // Returns the product if found, or a 404 Not Found otherwise
        return product
                .map(p -> new ResponseEntity<>(p, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
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