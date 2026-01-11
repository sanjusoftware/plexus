package com.bankengine.pricing.controller;

import com.bankengine.pricing.dto.PricingComponentRequest;
import com.bankengine.pricing.dto.PricingComponentResponse;
import com.bankengine.pricing.service.PricingComponentService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Pricing Component Management (Base)", description = "Manages the definition of reusable pricing items (e.g., Annual Fee, Interest Rate). Tiers are managed under the /tiers sub-resource.")
@RestController
@RequestMapping("/api/v1/pricing-components")
public class PricingComponentController {

    private final PricingComponentService pricingComponentService;

    public PricingComponentController(PricingComponentService pricingComponentService) {
        this.pricingComponentService = pricingComponentService;
    }

    @Operation(summary = "Retrieve all pricing components (with Tiers and Price Values)",
            description = "Returns a list of all reusable pricing component definitions, including their associated Tiers and Prices.")
    @ApiResponse(responseCode = "200", description = "List of components successfully retrieved.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = PricingComponentResponse.class)))) // Use new DTO
    @GetMapping
    @PreAuthorize("hasAuthority('pricing:component:read')")
    public ResponseEntity<List<PricingComponentResponse>> getAllPricingComponents() {
        List<PricingComponentResponse> componentResponseDtoList = pricingComponentService.findAllComponents();
        return ResponseEntity.ok(componentResponseDtoList);
    }

    @Operation(summary = "Retrieve a pricing component by ID (with Tiers and Price Values)",
            description = "Returns a single pricing component definition by ID, including its associated Tiers and Prices.")
    @ApiResponse(responseCode = "200", description = "Component successfully retrieved.",
            content = @Content(schema = @Schema(implementation = PricingComponentResponse.class))) // Use new DTO
    @ApiResponse(responseCode = "404", description = "Pricing component not found.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pricing:component:read')")
    public ResponseEntity<PricingComponentResponse> getPricingComponentById(
            @Parameter(description = "ID of the pricing component to retrieve", required = true)
            @PathVariable Long id) {
        PricingComponentResponse responseDto = pricingComponentService.getComponentById(id);
        return ResponseEntity.ok(responseDto);
    }

    /**
     * POST /api/v1/pricing-components
     * Defines a new global pricing component (e.g., 'FX Fee') using DTO and validation.
     */
    @Operation(summary = "Define a new global pricing component",
               description = "Creates the high-level pricing definition (e.g., 'FX Fee') before tiers are added.")
    @ApiResponse(responseCode = "201", description = "Pricing Component successfully created.",
                 content = @Content(schema = @Schema(implementation = PricingComponentResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation or business logic error (e.g., name conflict).")
    @PostMapping
    @PreAuthorize("hasAuthority('pricing:component:create')")
    public ResponseEntity<PricingComponentResponse> createComponent(@Valid @RequestBody PricingComponentRequest requestDto) {
        PricingComponentResponse createdComponent = pricingComponentService.createComponent(requestDto);
        return new ResponseEntity<>(createdComponent, HttpStatus.CREATED);
    }


    @Operation(summary = "Update an existing pricing component",
            description = "Updates the name and/or type of an existing pricing component.")
    @ApiResponse(responseCode = "200", description = "Pricing component successfully updated.",
            content = @Content(schema = @Schema(implementation = PricingComponentResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation or business logic error (e.g., invalid type).")
    @ApiResponse(responseCode = "404", description = "Component not found.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('pricing:component:update')")
    public ResponseEntity<PricingComponentResponse> updatePricingComponent(
            @Parameter(description = "ID of the pricing component to update", required = true)
            @PathVariable Long id,
            @Valid @RequestBody PricingComponentRequest requestDto) {

        PricingComponentResponse updatedComponent = pricingComponentService.updateComponent(id, requestDto);
        return new ResponseEntity<>(updatedComponent, HttpStatus.OK);
    }

    @Operation(summary = "Delete a pricing component (Dependency Checked)",
            description = "Deletes a pricing component by its ID.  Returns 404 if the ID doesn't exist at all, or 403 if the ID belongs to another bank. Fails with 409 Conflict if associated tiers or product links exist.")
    @ApiResponse(responseCode = "204", description = "Component successfully deleted.")
    @ApiResponse(responseCode = "403", description = "Forbidden: Tenant mismatch. You cannot access resources belonging to another bank.")
    @ApiResponse(responseCode = "404", description = "Not Found: Pricing component ID does not exist in the system.")
    @ApiResponse(responseCode = "409", description = "Conflict: Component is linked to existing tiers or products.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('pricing:component:delete')")
    public void deletePricingComponent(
            @Parameter(description = "ID of the pricing component to delete", required = true)
            @PathVariable Long id) {
        pricingComponentService.deletePricingComponent(id);
    }
}