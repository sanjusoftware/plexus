package com.bankengine.catalog.controller;

import com.bankengine.catalog.dto.CreateFeatureComponentRequestDto;
import com.bankengine.catalog.dto.FeatureComponentResponseDto;
import com.bankengine.catalog.service.FeatureComponentService;
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
}