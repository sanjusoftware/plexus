package com.bankengine.catalog.controller;

import com.bankengine.catalog.dto.*;
import com.bankengine.catalog.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
     * POST /api/v1/products
     * Creates a new core Product entry in the catalog.
     */
    @Operation(summary = "Create a new product definition",
            description = "Creates a new Product entity using a DTO and returns the details.")
    @ApiResponse(responseCode = "201", description = "Product successfully created",
            content = @Content(schema = @Schema(implementation = ProductResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation or business logic error.",
            content = @Content(schema = @Schema(implementation = String.class)))
    @PostMapping
    @PreAuthorize("hasAuthority('catalog:product:create')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest requestDto) {
        ProductResponse responseDto = productService.createProduct(requestDto);
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
    @PreAuthorize("hasAuthority('catalog:product:read')")
    public ResponseEntity<Page<ProductResponse>> searchProducts(@Valid ProductSearchRequestDto criteria) {
        return ResponseEntity.ok(productService.searchProducts(criteria));
    }

    /**
     * GET /api/v1/products/{id}
     * Retrieves a Product and its associated features/pricing.
     */
    @Operation(summary = "Retrieve a product by its unique ID",
            description = "Fetches the full details of a product, including associated features and pricing links.")
    @ApiResponse(responseCode = "200", description = "Product successfully retrieved.",
                 content = @Content(schema = @Schema(implementation = ProductResponse.class)))
    @ApiResponse(responseCode = "404", description = "Product not found with the given ID.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('catalog:product:read')")
    public ResponseEntity<ProductResponse> getProductById(
            @Parameter(description = "The unique ID of the product to retrieve", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductResponseById(id));
    }

    /**
     * POST /api/v1/products/{id}/features
     * Links a defined FeatureComponent to a specific Product instance.
     */
    @Operation(summary = "Link a FeatureComponent to a Product",
               description = "Establishes a connection between a Product and an existing FeatureComponent.")
    @ApiResponse(responseCode = "200", description = "Feature successfully linked to the product",
                 content = @Content(schema = @Schema(implementation = ProductResponse.class)))
    @ApiResponse(responseCode = "400", description = "Error linking feature.",
                 content = @Content(schema = @Schema(implementation = String.class)))
    @PostMapping("/{id}/features")
    @PreAuthorize("hasAuthority('catalog:product:update')")
    public ResponseEntity<ProductResponse> linkFeatureToProduct(
            @Parameter(description = "The unique ID of the product", required = true)
            @PathVariable Long id,
            @Valid @RequestBody ProductFeatureRequest dto) {
        return ResponseEntity.ok(productService.linkFeatureToProduct(id, dto));
    }

    /**
     * PUT /api/v1/products/{id}
     * Updates administrative fields only if product is in DRAFT.
     */
    @Operation(summary = "Update product metadata (DRAFT status only)",
            description = "Allows updates to administrative fields (name, bankId) only if the product status is DRAFT. Critical changes require versioning.")
    @ApiResponse(responseCode = "200", description = "Product successfully updated.")
    @ApiResponse(responseCode = "400", description = "Validation or business logic error.")
    @ApiResponse(responseCode = "403", description = "Update not allowed for current product status (ACTIVE/INACTIVE).")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('catalog:product:update')")
    public ResponseEntity<ProductResponse> updateProduct(
            @Parameter(description = "The unique ID of the product to update", required = true)
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest requestDto) {
        return ResponseEntity.ok(productService.updateProduct(id, requestDto));
    }

    /**
     * POST /api/v1/products/{id}/activate
     */
    @Operation(summary = "Activate a DRAFT product",
            description = "Sets the product status to ACTIVE and optionally sets/overrides the effective date.")
    @ApiResponse(responseCode = "200", description = "Product successfully activated.")
    @ApiResponse(responseCode = "400", description = "Product is not in DRAFT status.")
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('catalog:product:activate')")
    public ResponseEntity<ProductResponse> activateProduct(
            @Parameter(description = "ID of the product to activate", required = true)
            @PathVariable Long id,
            @Parameter(description = "Optional override for the effective date (ISO format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveDate) {

        ProductResponse responseDto = productService.activateProduct(id, effectiveDate);
        return ResponseEntity.ok(responseDto);
    }

    /**
     * POST /api/v1/products/{id}/deactivate
     */
    @Operation(summary = "Deactivate an ACTIVE product",
            description = "Sets the product status to INACTIVE and sets the expiration date to today.")
    @ApiResponse(responseCode = "200", description = "Product successfully deactivated.")
    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('catalog:product:deactivate')")
    public ResponseEntity<ProductResponse> deactivateProduct(
            @Parameter(description = "ID of the product to deactivate", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(productService.deactivateProduct(id));
    }

    /**
     * PUT /api/v1/products/{id}/expiration
     */
    @Operation(summary = "Extend product life",
            description = "Updates the expiration date of an ACTIVE/DRAFT product.")
    @ApiResponse(responseCode = "200", description = "Product expiration date successfully extended.")
    @PutMapping("/{id}/expiration")
    @PreAuthorize("hasAuthority('catalog:product:update')")
    public ResponseEntity<ProductResponse> extendProductExpiration(
            @Parameter(description = "ID of the product to extend", required = true)
            @PathVariable Long id,
            @Valid @RequestBody ProductExpirationDto dto) {
        return ResponseEntity.ok(productService.extendProductExpiration(id, dto.getExpirationDate()));
    }

    /**
     * PUT /api/v1/products/{id}/features
     */
    @Operation(summary = "Synchronize product features",
            description = "Sets the complete list of features for a product. Deletes removed features, updates existing, and creates new ones.")
    @ApiResponse(responseCode = "200", description = "Product features successfully synchronized.",
            content = @Content(schema = @Schema(implementation = ProductResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation or value type error.")
    @ApiResponse(responseCode = "404", description = "Product or Feature Component not found.")
    @PutMapping("/{id}/features")
    @PreAuthorize("hasAuthority('catalog:product:update')")
    public ResponseEntity<ProductResponse> syncProductFeatures(
            @Parameter(description = "The unique ID of the product to update", required = true)
            @PathVariable Long id,
            @Valid @RequestBody List<ProductFeatureRequest> requests) {
        return ResponseEntity.ok(productService.syncProductFeatures(id, requests));
    }

    /**
     * PUT /api/v1/products/{id}/pricing
     */
    @Operation(summary = "Synchronize product pricing components",
            description = "Sets the complete list of pricing components for a product. Deletes removed links and creates new ones.")
    @ApiResponse(responseCode = "200", description = "Product pricing successfully synchronized.",
            content = @Content(schema = @Schema(implementation = ProductResponse.class)))
    @ApiResponse(responseCode = "404", description = "Product or Pricing Component not found.")
    @PutMapping("/{id}/pricing")
    @PreAuthorize("hasAuthority('catalog:product:update')")
    public ResponseEntity<ProductResponse> syncProductPricing(
            @Parameter(description = "The unique ID of the product to update", required = true)
            @PathVariable Long id,
            @Valid @RequestBody List<ProductPricingRequest> requests) {
        ProductResponse response = productService.syncProductPricing(id, requests);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/products/{id}/new-version
     */
    @Operation(summary = "Create a new version of an ACTIVE product",
            description = "Archives the current product and creates a new DRAFT product inheriting its configuration. Used for structural changes.")
    @ApiResponse(responseCode = "201", description = "New product version successfully created.",
            content = @Content(schema = @Schema(implementation = ProductResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error.")
    @ApiResponse(responseCode = "403", description = "Product is not in ACTIVE status.")
    @PostMapping("/{id}/new-version")
    @PreAuthorize("hasAuthority('catalog:product:create')")
    public ResponseEntity<ProductResponse> createNewVersion(
            @Parameter(description = "The ID of the currently ACTIVE product to be versioned/replaced.", required = true)
            @PathVariable Long id,
            @Valid @RequestBody NewProductVersionRequest requestDto) {
        return new ResponseEntity<>(productService.createNewVersion(id, requestDto), HttpStatus.CREATED);
    }
}