package com.bankengine.catalog.controller;

import com.bankengine.catalog.converter.FeatureComponentMapper;
import com.bankengine.catalog.dto.FeatureComponentRequest;
import com.bankengine.catalog.dto.FeatureComponentResponse;
import com.bankengine.catalog.dto.VersionRequest;
import com.bankengine.catalog.service.FeatureComponentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Feature Component Management", description = "Defines and manages individual product features (e.g., 'Free FX', 'Premium Support') and their configurations.")
@RestController
@RequestMapping("/api/v1/features")
@RequiredArgsConstructor
public class FeatureComponentController {

    private final FeatureComponentService featureComponentService;
    private final FeatureComponentMapper featureComponentMapper;

    @Operation(summary = "Retrieve all defined feature components",
            description = "Returns a list of all reusable feature definitions in the catalog. Results are automatically scoped to the current Bank/Tenant ID.")
    @ApiResponse(responseCode = "200", description = "List of all feature components successfully retrieved.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = FeatureComponentResponse.class))))
    @GetMapping
    @PreAuthorize("hasAuthority('catalog:feature:read')")
    public ResponseEntity<List<FeatureComponentResponse>> getAllFeatures(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Integer version,
            @RequestParam(required = false) com.bankengine.common.model.VersionableEntity.EntityStatus status) {
        return ResponseEntity.ok(featureComponentService.searchFeatures(code, version, status));
    }

    @Operation(summary = "Retrieve a feature component by ID",
            description = "Fetches the full details of a specific feature component, including metadata like data types and current lifecycle status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feature component successfully retrieved.",
                    content = @Content(schema = @Schema(implementation = FeatureComponentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Feature component not found.")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('catalog:feature:read')")
    public ResponseEntity<FeatureComponentResponse> getFeatureById(
            @Parameter(description = "The unique ID of the feature component", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(featureComponentService.getFeatureResponseById(id));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("hasAuthority('catalog:feature:read')")
    public ResponseEntity<List<FeatureComponentResponse>> getFeatureByCode(
            @PathVariable String code,
            @RequestParam(required = false) Integer version) {
        return ResponseEntity.ok(featureComponentService.getFeaturesByCode(code, version));
    }

    @Operation(summary = "Create a new feature component",
            description = "Creates a new product feature definition in DRAFT status. These components are designed to be linked across multiple products or bundles.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Feature component successfully created.",
                    content = @Content(schema = @Schema(implementation = FeatureComponentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business logic error (e.g., duplicate name).")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('catalog:feature:create')")
    public ResponseEntity<FeatureComponentResponse> createFeature(@Valid @RequestBody FeatureComponentRequest requestDto) {
        return new ResponseEntity<>(featureComponentService.createFeature(requestDto), HttpStatus.CREATED);
    }

    @Operation(summary = "Partial update of a DRAFT feature",
            description = "Allows in-place updates of the name, description, or data type of an existing feature component. Only permitted while the status is DRAFT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feature component successfully updated.",
                    content = @Content(schema = @Schema(implementation = FeatureComponentResponse.class))),
            @ApiResponse(responseCode = "403", description = "Modification blocked: Feature is not in DRAFT status."),
            @ApiResponse(responseCode = "404", description = "Feature component not found.")
    })
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('catalog:feature:update')")
    public ResponseEntity<FeatureComponentResponse> patchFeature(
            @Parameter(description = "ID of the DRAFT feature component to update", required = true)
            @PathVariable Long id,
            @Valid @RequestBody FeatureComponentRequest requestDto) {
        return ResponseEntity.ok(featureComponentService.updateFeature(id, requestDto));
    }

    @Operation(summary = "Full update of a DRAFT feature",
            description = "Allows replacing the entire DRAFT feature component. Only permitted while the status is DRAFT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feature component successfully updated.",
                    content = @Content(schema = @Schema(implementation = FeatureComponentResponse.class))),
            @ApiResponse(responseCode = "403", description = "Modification blocked: Feature is not in DRAFT status."),
            @ApiResponse(responseCode = "404", description = "Feature component not found.")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('catalog:feature:update')")
    public ResponseEntity<FeatureComponentResponse> updateFeature(
            @Parameter(description = "ID of the DRAFT feature component to update", required = true)
            @PathVariable Long id,
            @Valid @RequestBody FeatureComponentRequest requestDto) {
        return ResponseEntity.ok(featureComponentService.updateFeature(id, requestDto));
    }

    @Operation(summary = "Create a new version of an existing feature component",
            description = "Creates a new DRAFT version from an existing product feature. Use this for modifying definitions that are already ACTIVE to ensure historical integrity.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "New version created successfully.",
                    content = @Content(schema = @Schema(implementation = FeatureComponentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Source feature component not found.")
    })
    @PostMapping("/{id}/new-version")
    @PreAuthorize("hasAuthority('catalog:feature:create')")
    public ResponseEntity<FeatureComponentResponse> versionFeature(
            @Parameter(description = "ID of the source feature component to template from", required = true)
            @PathVariable Long id,
            @Valid @RequestBody VersionRequest requestDto) {
        return new ResponseEntity<>(featureComponentService.versionFeature(id, requestDto), HttpStatus.CREATED);
    }

    @Operation(summary = "Activate a feature component",
            description = "Transitions a DRAFT feature to ACTIVE status, making it available for linkage to products and bundles.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feature component successfully activated.",
                    content = @Content(schema = @Schema(implementation = FeatureComponentResponse.class))),
            @ApiResponse(responseCode = "403", description = "Unauthorized or invalid state transition.")
    })
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('catalog:feature:activate')")
    public ResponseEntity<FeatureComponentResponse> activateFeature(@PathVariable Long id) {
        return ResponseEntity.ok(featureComponentService.activateFeature(id));
    }

    @Operation(summary = "Delete or Archive a feature component",
            description = "Physically deletes a DRAFT feature. If the feature is ACTIVE, it transitions it to ARCHIVED status. This fails (409) if the feature is currently linked to any products.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Feature successfully deleted or archived."),
            @ApiResponse(responseCode = "404", description = "Feature not found."),
            @ApiResponse(responseCode = "409", description = "Conflict: Feature is linked to products.")
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('catalog:feature:delete')")
    public void deleteFeature(@PathVariable Long id) {
        featureComponentService.deleteFeature(id);
    }
}
