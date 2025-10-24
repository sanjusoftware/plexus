package com.bankengine.catalog.controller;

import com.bankengine.catalog.dto.CreateProductRequestDto;
import com.bankengine.catalog.dto.ProductFeatureDto;
import com.bankengine.catalog.dto.ProductResponseDto;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Product Management", description = "Operations for creating and managing product definitions.")
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * POST /api/v1/products
     * Creates a new core Product entry in the catalog.
     */
    @Operation(summary = "Create a new product definition",
               description = "Creates a new Product entity using a DTO and returns the details.")
    @ApiResponse(responseCode = "201", description = "Product successfully created",
                 content = @Content(schema = @Schema(implementation = ProductResponseDto.class)))
    @ApiResponse(responseCode = "400", description = "Validation or business logic error.",
                 content = @Content(schema = @Schema(implementation = String.class)))
    @PostMapping
    public ResponseEntity<ProductResponseDto> createProduct(@Valid @RequestBody CreateProductRequestDto requestDto) {
        ProductResponseDto responseDto = productService.createProduct(requestDto);
        return new ResponseEntity<>(responseDto, HttpStatus.CREATED);
    }

    /**
     * GET /api/v1/products/{id}
     * Retrieves a Product and its associated features, returning a DTO.
     */
    @Operation(summary = "Retrieve a product by its unique ID",
            description = "Fetches the full details of a product, including associated features.")
    @ApiResponse(responseCode = "200", description = "Product successfully retrieved.",
                 content = @Content(schema = @Schema(implementation = ProductResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "Product not found with the given ID.")
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDto> getProductById(
            @Parameter(description = "The unique ID of the product to retrieve", required = true)
            @PathVariable Long id) {

        ProductResponseDto responseDto = productService.getProductResponseById(id);
        // Assuming the service layer or GlobalExceptionHandler handles the 404/not found case.
        return new ResponseEntity<>(responseDto, HttpStatus.OK);
    }

    /**
     * POST /api/v1/products/link-feature
     * Links a defined FeatureComponent to a specific Product instance.
     */
    @Operation(summary = "Link a FeatureComponent to a Product",
               description = "Establishes a connection between a Product and an existing FeatureComponent.")
    @ApiResponse(responseCode = "201", description = "Feature successfully linked to the product",
                 content = @Content(schema = @Schema(implementation = ProductFeatureLink.class)))
    @ApiResponse(responseCode = "400", description = "Error linking feature.",
                 content = @Content(schema = @Schema(implementation = String.class)))
    @PostMapping("/link-feature")
    public ResponseEntity<ProductFeatureLink> linkFeatureToProduct(@RequestBody ProductFeatureDto dto) {
        ProductFeatureLink link = productService.linkFeatureToProduct(dto);
        return new ResponseEntity<>(link, HttpStatus.CREATED);
    }
}