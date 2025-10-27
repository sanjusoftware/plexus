package com.bankengine.catalog.controller;

import com.bankengine.catalog.dto.CreateProductTypeRequestDto;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.service.ProductTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// --- Swagger Annotations Added Here ---
@Tag(name = "Product Type Management", description = "Provides read-only access to available product category types (e.g., CASA, Loan, Credit Card).")
@RestController
@RequestMapping("/api/v1/product-types")
public class ProductTypeController {

    private final ProductTypeService productTypeService;

    public ProductTypeController(ProductTypeService productTypeService) {
        this.productTypeService = productTypeService;
    }

    /**
     * GET /api/v1/product-types : Retrieves a list of all product types.
     */
    @Operation(summary = "Retrieve all available product types",
            description = "Returns a list of all high-level product categories used in the bank's catalog. Useful for populating dropdowns.")
    @ApiResponse(responseCode = "200", description = "List of product types successfully retrieved.",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ProductType.class))) // NOTE: Using the Entity class as the response schema
    @GetMapping
    public ResponseEntity<List<ProductType>> getAllProductTypes() {
        List<ProductType> productTypes = productTypeService.findAllProductTypes();

        return ResponseEntity.ok(productTypes);
    }

    // --- POST /api/v1/product-types (New Method) ---
    @Operation(summary = "Create a new product type",
            description = "Adds a new high-level product category to the catalog.")
    @ApiResponse(responseCode = "201", description = "Product Type successfully created.",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ProductType.class)))
    @ApiResponse(responseCode = "400", description = "Validation error (e.g., name constraints violated).")
    @PostMapping
    public ResponseEntity<ProductType> createProductType(@Valid @RequestBody CreateProductTypeRequestDto requestDto) {
        ProductType createdType = productTypeService.createProductType(requestDto);
        // Returns 201 Created
        return new ResponseEntity<>(createdType, HttpStatus.CREATED);
    }
}