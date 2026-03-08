package com.bankengine.catalog.controller;

import com.bankengine.catalog.converter.ProductMapper;
import com.bankengine.catalog.dto.ProductRequest;
import com.bankengine.catalog.dto.ProductResponse;
import com.bankengine.catalog.dto.ProductSearchRequest;
import com.bankengine.catalog.dto.VersionRequest;
import com.bankengine.catalog.service.ProductService;
import com.bankengine.web.dto.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "Product Management", description = "Operations for creating and managing lifecycle-versioned product definitions.")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductMapper productMapper;

    @Operation(summary = "Create a new product definition",
            description = "Creates a new Product entity in DRAFT status. This is an aggregate creation: include all Feature and Pricing links in the Request DTO to initialize the product fully.")
    @ApiResponse(responseCode = "201", description = "Product successfully created",
            content = @Content(schema = @Schema(implementation = ProductResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation or business logic error.")
    @ApiResponse(responseCode = "401", description = "Authentication required.")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions to create products.")
    @ApiResponse(responseCode = "409", description = "Conflict: A product with this code already exists for the current bank.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping
    @PreAuthorize("hasAuthority('catalog:product:create')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest requestDto) {
        return new ResponseEntity<>(productService.createProduct(requestDto), HttpStatus.CREATED);
    }

    @Operation(summary = "Search and filter products",
            description = "Retrieves a paginated list of products. Supports filtering by metadata, status, and date ranges. Ideal for catalog browsing.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved list of products.")
    @GetMapping
    @PreAuthorize("hasAuthority('catalog:product:read')")
    public ResponseEntity<Page<ProductResponse>> searchProducts(@Valid ProductSearchRequest criteria) {
        return ResponseEntity.ok(productService.searchProducts(criteria));
    }

    @Operation(summary = "Retrieve a product by its unique ID",
            description = "Fetches complete product details including its current version, status, and the deep-tree of features and pricing components.")
    @ApiResponse(responseCode = "200", description = "Product details successfully retrieved.")
    @ApiResponse(responseCode = "404", description = "Product not found for the provided ID.")
    @GetMapping("/{productId}")
    @PreAuthorize("hasAuthority('catalog:product:read')")
    public ResponseEntity<ProductResponse> getProductById(
            @Parameter(description = "The unique ID of the product to retrieve", required = true)
            @PathVariable Long productId) {
        return ResponseEntity.ok(productService.getProductResponseById(productId));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("hasAuthority('catalog:product:read')")
    public ResponseEntity<ProductResponse> getProductByCode(
            @PathVariable String code,
            @RequestParam(required = false) Integer version) {
        return ResponseEntity.ok(productMapper.toResponse(
                productService.getProductEntityByCode(code, version)));
    }

    @Operation(summary = "Partial update of a DRAFT product (Simplified Aggregate Update)",
            description = "The consolidated entry point for all modifications. Send only the fields you wish to change. " +
                    "To extend life, send only the 'expirationDate'. To sync features/pricing, send the respective lists. " +
                    "Only allowed while status is DRAFT.")
    @ApiResponse(responseCode = "200", description = "Product aggregate successfully updated.",
            content = @Content(schema = @Schema(implementation = ProductResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error (e.g., invalid date or value type mismatch).")
    @ApiResponse(responseCode = "403", description = "Modification blocked: Product is ACTIVE or ARCHIVED.")
    @ApiResponse(responseCode = "404", description = "Product not found.")
    @PatchMapping("/{productId}")
    @PreAuthorize("hasAuthority('catalog:product:update')")
    public ResponseEntity<ProductResponse> patchProduct(
            @Parameter(description = "The unique ID of the DRAFT product to update", required = true)
            @PathVariable Long productId,
            @RequestBody ProductRequest requestDto) {
        return ResponseEntity.ok(productService.updateProduct(productId, requestDto));
    }

    @Operation(summary = "Version or Branch a product",
            description = "Deep-clones the product into a new DRAFT. Use this to modify ACTIVE products. " +
                    "If 'newCode' is provided, it starts a new lineage (Branch). If omitted, it increments the version (Revision).")
    @ApiResponse(responseCode = "201", description = "New product version/branch successfully created.",
            content = @Content(schema = @Schema(implementation = ProductResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid version request.")
    @ApiResponse(responseCode = "404", description = "Source product not found.")
    @PostMapping("/{id}/version")
    @PreAuthorize("hasAuthority('catalog:product:create')")
    public ResponseEntity<ProductResponse> versionProduct(
            @Parameter(description = "ID of the source product to use as a template", required = true)
            @PathVariable Long id,
            @Valid @RequestBody VersionRequest requestDto) {
        return new ResponseEntity<>(productService.cloneProduct(id, requestDto), HttpStatus.CREATED);
    }

    @Operation(summary = "Activate a DRAFT product",
            description = "Transitions status to ACTIVE. Triggers cache eviction for the public catalog. Product becomes immutable for direct updates.")
    @ApiResponse(responseCode = "200", description = "Product successfully activated.")
    @ApiResponse(responseCode = "400", description = "Activation failed (e.g., status is not DRAFT).")
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('catalog:product:activate')")
    public ResponseEntity<ProductResponse> activateProduct(
            @Parameter(description = "ID of the product to activate", required = true)
            @PathVariable Long id,
            @Parameter(description = "Optional override for the effective date (ISO: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate activationDate) {
        return ResponseEntity.ok(productService.activateProduct(id, activationDate));
    }

    @Operation(summary = "Archive/Deactivate an ACTIVE product",
            description = "Moves product to ARCHIVED status. This is a terminal state. Sets the expiration date to today.")
    @ApiResponse(responseCode = "200", description = "Product successfully archived.")
    @ApiResponse(responseCode = "403", description = "Unauthorized or invalid state transition.")
    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('catalog:product:deactivate')")
    public ResponseEntity<ProductResponse> deactivateProduct(
            @Parameter(description = "ID of the product to deactivate", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(productService.deactivateProduct(id));
    }

    @PostMapping("/{id}/extend-expiry")
    @PreAuthorize("hasAuthority('catalog:product:update')")
    public ResponseEntity<ProductResponse> extendExpiry(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate newExpiryDate) {
        return ResponseEntity.ok(productService.extendProductExpiry(id, newExpiryDate));
    }
}
