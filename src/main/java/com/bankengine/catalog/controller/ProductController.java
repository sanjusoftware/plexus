package com.bankengine.catalog.controller;

import com.bankengine.catalog.dto.*;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

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
     * GET /api/v1/products
     * Retrieves a list of products, allowing for dynamic filtering, pagination, and sorting.
     */
    @Operation(summary = "Search and filter products",
            description = "Retrieves products based on metadata, date ranges, status, with pagination.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved list of products.")
    @GetMapping
    public ResponseEntity<Page<ProductResponseDto>> searchProducts(
            @Valid ProductSearchRequestDto criteria) { // Spring automatically maps query params to this DTO
        Page<ProductResponseDto> productPage = productService.searchProducts(criteria);
        return new ResponseEntity<>(productPage, HttpStatus.OK);
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
    @ApiResponse(responseCode = "200", description = "Feature successfully linked to the product",
                 content = @Content(schema = @Schema(implementation = ProductResponseDto.class)))
    @ApiResponse(responseCode = "400", description = "Error linking feature.",
                 content = @Content(schema = @Schema(implementation = String.class)))
    @PostMapping("/link-feature")
    public ResponseEntity<ProductResponseDto> linkFeatureToProduct(@RequestBody ProductFeatureDto dto) {
        ProductResponseDto responseDto = productService.linkFeatureToProduct(dto);
        return new ResponseEntity<>(responseDto, HttpStatus.OK);
    }

    // /api/v1/products/{id} (RESTRICTED METADATA UPDATE)
    @Operation(summary = "Update product metadata (DRAFT status only)",
            description = "Allows updates to administrative fields (name, bankId) only if the product status is DRAFT. Critical changes require versioning.")
    @ApiResponse(responseCode = "200", description = "Product successfully updated.")
    @ApiResponse(responseCode = "400", description = "Validation or business logic error.")
    @ApiResponse(responseCode = "403", description = "Update not allowed for current product status (ACTIVE/INACTIVE).")
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponseDto> updateProduct(
            @Parameter(description = "The unique ID of the product to update", required = true)
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequestDto requestDto) {

        ProductResponseDto responseDto = productService.updateProduct(id, requestDto);
        return new ResponseEntity<>(responseDto, HttpStatus.OK);
    }

    // 💡 NEW POST /api/v1/products/{id}/activate (DIRECT ACTION 1)
    @Operation(summary = "Activate a DRAFT product",
            description = "Sets the product status to ACTIVE and optionally sets the effective date.")
    @ApiResponse(responseCode = "200", description = "Product successfully activated.")
    @ApiResponse(responseCode = "400", description = "Product is not in DRAFT status.")
    @PostMapping("/{id}/activate")
    public ResponseEntity<ProductResponseDto> activateProduct(
            @Parameter(description = "ID of the product to activate", required = true)
            @PathVariable Long id,
            @RequestBody(required = false) ProductActivationDto dto) { // DTO might only contain effectiveDate

        LocalDate effectiveDate = (dto != null) ? dto.getEffectiveDate() : null;
        ProductResponseDto responseDto = productService.activateProduct(id, effectiveDate);
        return new ResponseEntity<>(responseDto, HttpStatus.OK);
    }

    // /api/v1/products/{id}/deactivate (DIRECT ACTION 2)
    @Operation(summary = "Deactivate an ACTIVE product",
            description = "Sets the product status to INACTIVE and sets the expiration date to today.")
    @ApiResponse(responseCode = "200", description = "Product successfully deactivated.")
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ProductResponseDto> deactivateProduct(
            @Parameter(description = "ID of the product to deactivate", required = true)
            @PathVariable Long id) {

        ProductResponseDto responseDto = productService.deactivateProduct(id);
        return new ResponseEntity<>(responseDto, HttpStatus.OK);
    }

    // /api/v1/products/{id}/expiration (DIRECT ACTION 3)
    @Operation(summary = "Extend product life",
            description = "Updates the expiration date of an ACTIVE/DRAFT product.")
    @ApiResponse(responseCode = "200", description = "Product expiration date successfully extended.")
    @PutMapping("/{id}/expiration")
    public ResponseEntity<ProductResponseDto> extendProductExpiration(
            @Parameter(description = "ID of the product to extend", required = true)
            @PathVariable Long id,
            // Use the new DTO for consistency and validation
            @Valid @RequestBody ProductExpirationDto dto) {

        ProductResponseDto responseDto = productService.extendProductExpiration(id, dto.getNewExpirationDate());
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

    // 💡 NEW POST /api/v1/products/{id}/new-version (COPY-AND-UPDATE)
    @Operation(summary = "Create a new version of an ACTIVE product",
            description = "Archives the current product and creates a new DRAFT product inheriting its configuration. Used for structural changes.")
    @ApiResponse(responseCode = "201", description = "New product version successfully created.",
            content = @Content(schema = @Schema(implementation = ProductResponseDto.class)))
    @ApiResponse(responseCode = "400", description = "Validation error.")
    @ApiResponse(responseCode = "403", description = "Product is not in ACTIVE status.")
    @PostMapping("/{id}/new-version")
    public ResponseEntity<ProductResponseDto> createNewVersion(
            @Parameter(description = "The ID of the currently ACTIVE product to be versioned/replaced.", required = true)
            @PathVariable Long id,
            @Valid @RequestBody CreateNewVersionRequestDto requestDto) {

        ProductResponseDto responseDto = productService.createNewVersion(id, requestDto);
        return new ResponseEntity<>(responseDto, HttpStatus.CREATED);
    }
}