package com.bankengine.pricing.controller;

import com.bankengine.pricing.dto.MetadataResponse;
import com.bankengine.pricing.dto.PricingMetadataRequest;
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

@Tag(name = "Pricing Input Metadata Management", description = "Manages the definition of input attributes (keys and data types) used to write Tier Conditions.")
@RestController
@RequestMapping("/api/v1/pricing-metadata")
public class PricingInputMetadataController {

    private final PricingInputMetadataService metadataService;

    public PricingInputMetadataController(PricingInputMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    // --- POST: Create Metadata ---
    @Operation(summary = "Create new pricing input metadata",
               description = "Registers a new attribute (key and data type) that can be referenced in pricing tiers.")
    @ApiResponse(responseCode = "201", description = "Metadata successfully created.",
                 content = @Content(schema = @Schema(implementation = MetadataResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error (e.g., key already exists or invalid data type).")
    @PostMapping
    @PreAuthorize("hasAuthority('pricing:metadata:create')")
    public ResponseEntity<MetadataResponse> createMetadata(@Valid @RequestBody PricingMetadataRequest requestDto) {
        MetadataResponse createdMetadata = metadataService.createMetadata(requestDto);
        return new ResponseEntity<>(createdMetadata, HttpStatus.CREATED);
    }

    // --- GET: Retrieve All Metadata ---
    @Operation(summary = "Retrieve all pricing input metadata",
               description = "Returns a list of all defined attributes and their data types.")
    @ApiResponse(responseCode = "200", description = "List of metadata successfully retrieved.")
    @GetMapping
    @PreAuthorize("hasAuthority('pricing:metadata:read')")
    public ResponseEntity<List<MetadataResponse>> getAllMetadata() {
        List<MetadataResponse> metadataList = metadataService.findAllMetadata();
        return ResponseEntity.ok(metadataList);
    }

    // --- GET: Retrieve Metadata by Key ---
    @Operation(summary = "Retrieve pricing input metadata by key",
               description = "Returns a single metadata definition by its unique attribute key.")
    @ApiResponse(responseCode = "200", description = "Metadata successfully retrieved.")
    @ApiResponse(responseCode = "404", description = "Metadata key not found.")
    @GetMapping("/{attributeKey}")
    @PreAuthorize("hasAuthority('pricing:metadata:read')")
    public ResponseEntity<MetadataResponse> getMetadataByKey(
            @Parameter(description = "Unique attribute key (e.g., 'customerSegment')", required = true)
            @PathVariable String attributeKey) {
        MetadataResponse responseDto = metadataService.getMetadataByKey(attributeKey);
        return ResponseEntity.ok(responseDto);
    }

    // --- PUT: Update Metadata ---
    @Operation(summary = "Update pricing input metadata",
               description = "Updates the display name or data type of an existing attribute key.")
    @ApiResponse(responseCode = "200", description = "Metadata successfully updated.")
    @ApiResponse(responseCode = "400", description = "Validation error.")
    @ApiResponse(responseCode = "404", description = "Metadata key not found.")
    @PutMapping("/{attributeKey}")
    @PreAuthorize("hasAuthority('pricing:metadata:update')")
    public ResponseEntity<MetadataResponse> updateMetadata(
            @Parameter(description = "Unique attribute key (e.g., 'customerSegment')", required = true)
            @PathVariable String attributeKey,
            @Valid @RequestBody PricingMetadataRequest requestDto) {
        MetadataResponse updatedMetadata = metadataService.updateMetadata(attributeKey, requestDto);
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