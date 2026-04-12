package com.bankengine.pricing.controller;

import com.bankengine.pricing.dto.PricingMetadataRequest;
import com.bankengine.pricing.dto.PricingMetadataResponse;
import com.bankengine.pricing.service.PricingInputMetadataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Pricing Input Metadata Management", description = "Manages the user-defined pricing attribute registry used by tier conditions, including how each attribute maps to pricing request facts or custom attributes.")
@RestController
@RequestMapping("/api/v1/pricing-metadata")
public class PricingInputMetadataController {

    private final PricingInputMetadataService metadataService;

    public PricingInputMetadataController(PricingInputMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    // --- POST: Create Metadata ---
    @Operation(summary = "Create new pricing input metadata",
               description = "Registers a new pricing attribute key that users can reference in pricing tiers, along with its data type and source mapping.")
    @ApiResponse(responseCode = "201", description = "Metadata successfully created.",
                 content = @Content(schema = @Schema(implementation = PricingMetadataResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error (e.g., key already exists or invalid data type).")
    @PostMapping
    @PreAuthorize("hasAuthority('pricing:metadata:create')")
    public ResponseEntity<PricingMetadataResponse> createMetadata(@Valid @RequestBody PricingMetadataRequest requestDto) {
        PricingMetadataResponse createdMetadata = metadataService.createMetadata(requestDto);
        return new ResponseEntity<>(createdMetadata, HttpStatus.CREATED);
    }

    // --- GET: Retrieve All Metadata ---
    @Operation(summary = "Retrieve all pricing input metadata",
               description = "Returns a list of all registered pricing attributes, including source mapping details used by the rules engine.")
    @ApiResponse(responseCode = "200", description = "List of metadata successfully retrieved.")
    @GetMapping
    @PreAuthorize("hasAuthority('pricing:metadata:read')")
    public ResponseEntity<List<PricingMetadataResponse>> getAllMetadata() {
        List<PricingMetadataResponse> metadataList = metadataService.findAllMetadata();
        return ResponseEntity.ok(metadataList);
    }

    @Operation(summary = "Retrieve canonical system pricing attribute keys",
            description = "Returns server-defined canonical keys used by pricing runtime for system inputs.")
    @ApiResponse(responseCode = "200", description = "System pricing keys successfully retrieved.")
    @GetMapping("/system-attributes")
    @PreAuthorize("hasAuthority('pricing:metadata:read')")
    public ResponseEntity<List<String>> getSystemAttributes() {
        return ResponseEntity.ok(metadataService.getSystemAttributeKeys());
    }

    // --- GET: Retrieve Metadata by Key ---
    @Operation(summary = "Retrieve pricing input metadata by key",
               description = "Returns a single pricing attribute definition by its unique attribute key.")
    @ApiResponse(responseCode = "200", description = "Metadata successfully retrieved.")
    @ApiResponse(responseCode = "404", description = "Metadata key not found.")
    @GetMapping("/{attributeKey}")
    @PreAuthorize("hasAuthority('pricing:metadata:read')")
    public ResponseEntity<PricingMetadataResponse> getMetadataByKey(
            @Parameter(description = "Unique attribute key selected in tier conditions (e.g., 'customerSegment')", required = true)
            @PathVariable String attributeKey) {
        PricingMetadataResponse responseDto = metadataService.getMetadataByKey(attributeKey);
        return ResponseEntity.ok(responseDto);
    }

    // --- PUT: Update Metadata ---
    @Operation(summary = "Update pricing input metadata",
               description = "Updates the display name, data type, or source mapping of an existing pricing attribute key.")
    @ApiResponse(responseCode = "200", description = "Metadata successfully updated.")
    @ApiResponse(responseCode = "400", description = "Validation error.")
    @ApiResponse(responseCode = "404", description = "Metadata key not found.")
    @PutMapping("/{attributeKey}")
    @PreAuthorize("hasAuthority('pricing:metadata:update')")
    public ResponseEntity<PricingMetadataResponse> updateMetadata(
            @Parameter(description = "Unique pricing attribute key to update", required = true)
            @PathVariable String attributeKey,
            @Valid @RequestBody PricingMetadataRequest requestDto) {
        PricingMetadataResponse updatedMetadata = metadataService.updateMetadata(attributeKey, requestDto);
        return new ResponseEntity<>(updatedMetadata, HttpStatus.OK);
    }

    // --- DELETE: Delete Metadata ---
    @Operation(summary = "Delete pricing input metadata",
               description = "Deletes a metadata key. Fails with 409 Conflict if it's referenced in any active Tier Condition.")
    @ApiResponse(responseCode = "204", description = "Metadata successfully deleted (No Content).")
    @ApiResponse(responseCode = "404", description = "Metadata key not found.")
    @ApiResponse(responseCode = "409", description = "Conflict: Metadata is currently used by one or more pricing tiers.")
    @DeleteMapping("/{attributeKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('pricing:metadata:delete')")
    public void deleteMetadata(
            @Parameter(description = "Unique attribute key to delete", required = true)
            @PathVariable String attributeKey) {
        metadataService.deleteMetadata(attributeKey);
    }
}
