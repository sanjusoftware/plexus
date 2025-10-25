package com.bankengine.catalog.controller;

import com.bankengine.catalog.dto.CreateFeatureComponentRequestDto;
import com.bankengine.catalog.dto.FeatureComponentResponseDto;
import com.bankengine.catalog.dto.UpdateFeatureComponentRequestDto;
import com.bankengine.catalog.service.FeatureComponentService;
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

import java.util.List;

@Tag(name = "Feature Component Management", description = "Defines and manages individual product features (e.g., 'Free FX', 'Premium Support') and their configurations.")
@RestController
@RequestMapping("/api/v1/features")
public class FeatureComponentController {

    private final FeatureComponentService featureComponentService;

    public FeatureComponentController(FeatureComponentService featureComponentService) {
        this.featureComponentService = featureComponentService;
    }

    /**
     * POST /api/v1/features
     * Creates a new global, reusable feature component definition using a DTO.
     */
    @Operation(summary = "Create a new reusable feature component",
               description = "Defines a generic feature that can be linked to multiple products.")
    @ApiResponse(responseCode = "201", description = "Feature component successfully created.",
                 content = @Content(schema = @Schema(implementation = FeatureComponentResponseDto.class)))
    @ApiResponse(responseCode = "400", description = "Validation or business logic error (e.g., duplicate name).")
    @PostMapping
    public ResponseEntity<FeatureComponentResponseDto> createFeature(@Valid @RequestBody CreateFeatureComponentRequestDto requestDto) {
        FeatureComponentResponseDto createdComponent = featureComponentService.createFeature(requestDto);
        return new ResponseEntity<>(createdComponent, HttpStatus.CREATED);
    }

    /**
     * GET /api/v1/features
     * Retrieves all defined feature components, returning a list of DTOs.
     */
    @Operation(summary = "Retrieve all defined feature components",
               description = "Returns a list of all reusable feature definitions in the catalog.")
    @ApiResponse(responseCode = "200", description = "List of all feature components successfully retrieved.")
    @GetMapping
    public ResponseEntity<List<FeatureComponentResponseDto>> getAllFeatures() {
        List<FeatureComponentResponseDto> features = featureComponentService.getAllFeatures();
        return new ResponseEntity<>(features, HttpStatus.OK);
    }

    @Operation(summary = "Retrieve a feature component by ID",
            description = "Fetches the details of a specific feature component.")
    @ApiResponse(responseCode = "200", description = "Feature component successfully retrieved.")
    @ApiResponse(responseCode = "404", description = "Feature component not found.")
    @GetMapping("/{id}")
    public ResponseEntity<FeatureComponentResponseDto> getFeatureById(
            @Parameter(description = "ID of the feature component", required = true)
            @PathVariable Long id) {
        FeatureComponentResponseDto responseDto = featureComponentService.getFeatureResponseById(id);
        return new ResponseEntity<>(responseDto, HttpStatus.OK);
    }

    // --- PUT (New Method) ---

    @Operation(summary = "Update an existing feature component",
            description = "Updates the name and/or data type of an existing feature component.")
    @ApiResponse(responseCode = "200", description = "Feature component successfully updated.",
            content = @Content(schema = @Schema(implementation = FeatureComponentResponseDto.class)))
    @ApiResponse(responseCode = "400", description = "Validation or business logic error (e.g., invalid data type).")
    @ApiResponse(responseCode = "404", description = "Feature component not found.")
    @PutMapping("/{id}")
    public ResponseEntity<FeatureComponentResponseDto> updateFeature(
            @Parameter(description = "ID of the feature component to update", required = true)
            @PathVariable Long id,
            @Valid @RequestBody UpdateFeatureComponentRequestDto requestDto) {

        FeatureComponentResponseDto updatedComponent = featureComponentService.updateFeature(id, requestDto);
        return new ResponseEntity<>(updatedComponent, HttpStatus.OK);
    }

    // --- DELETE (New Method) ---

    @Operation(summary = "Delete a feature component",
            description = "Deletes a feature component by its ID. (NOTE: May fail if linked to a product.)")
    @ApiResponse(responseCode = "204", description = "Feature component successfully deleted (No Content).")
    @ApiResponse(responseCode = "404", description = "Feature component not found.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFeature(
            @Parameter(description = "ID of the feature component to delete", required = true)
            @PathVariable Long id) {
        featureComponentService.deleteFeature(id);
    }
}