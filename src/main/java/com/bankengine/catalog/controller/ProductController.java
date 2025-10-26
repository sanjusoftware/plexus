package com.bankengine.catalog.controller;

import com.bankengine.catalog.dto.*;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "Product Management", description = "Operations for creating and managing product definitions.")
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * GET /api/v1/products
     * Retrieves all products in the catalog.
     */
    @Operation(summary = "Get a list of all products",
            description = "Retrieves all products currently defined in the catalog.")
    @ApiResponse(responseCode = "200", description = "List of products successfully retrieved.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProductResponseDto.class))))
    @GetMapping
    public ResponseEntity<List<ProductResponseDto>> getAllProducts() {
        List<ProductResponseDto> products = productService.findAllProducts();
        return new ResponseEntity<>(products, HttpStatus.OK);
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

    /**
     * DELETE /api/v1/products/{id} (Logical Deletion/Archival)
     * Archives a product by setting its status to 'ARCHIVED'.
     */
    @Operation(summary = "Archive a product (Logical Deletion)",
            description = "Sets the status of the product to 'ARCHIVED' instead of physical deletion.")
    @ApiResponse(responseCode = "200", description = "Product successfully archived.",
            content = @Content(schema = @Schema(implementation = ProductResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "Product not found with the given ID.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ProductResponseDto> archiveProduct(
            @Parameter(description = "The unique ID of the product to archive", required = true)
            @PathVariable Long id) {

        ProductResponseDto responseDto = productService.archiveProduct(id);
        return new ResponseEntity<>(responseDto, HttpStatus.OK);
    }

    /**
     * PUT /api/v1/products/{id}/features
     * Synchronizes the list of features linked to a product.
     * This handles creation, update, and deletion of links in one transaction.
     */
    @Operation(summary = "Synchronize product features",
            description = "Sets the complete list of features for a product. Deletes removed features, updates existing, and creates new ones.")
    @ApiResponse(responseCode = "200", description = "Product features successfully synchronized.",
            content = @Content(schema = @Schema(implementation = ProductResponseDto.class)))
    @ApiResponse(responseCode = "400", description = "Validation or value type error.")
    @ApiResponse(responseCode = "404", description = "Product or Feature Component not found.")
    @PutMapping("/{id}/features")
    public ResponseEntity<ProductResponseDto> syncProductFeatures(
            @Parameter(description = "The unique ID of the product to update", required = true)
            @PathVariable Long id,
            @Valid @RequestBody ProductFeatureSyncDto syncDto) {

        ProductResponseDto responseDto = productService.syncProductFeatures(id, syncDto.getFeatures());
        return new ResponseEntity<>(responseDto, HttpStatus.OK);
    }

    /**
     * PUT /api/v1/products/{id}/pricing
     * Synchronizes the list of pricing components linked to a product.
     * This handles creation and deletion of links in one transaction.
     */
    @Operation(summary = "Synchronize product pricing components",
            description = "Sets the complete list of pricing components for a product. Deletes removed links and creates new ones.")
    @ApiResponse(responseCode = "200", description = "Product pricing successfully synchronized.",
            content = @Content(schema = @Schema(implementation = ProductResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "Product or Pricing Component not found.")
    @PutMapping("/{id}/pricing")
    public ResponseEntity<ProductResponseDto> syncProductPricing(
            @Parameter(description = "The unique ID of the product to update", required = true)
            @PathVariable Long id,
            @Valid @RequestBody ProductPricingSyncDto syncDto) {

        ProductResponseDto responseDto = productService.syncProductPricing(id, syncDto.getPricingComponents());
        return new ResponseEntity<>(responseDto, HttpStatus.OK);
    }
}