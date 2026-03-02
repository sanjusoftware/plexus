package com.bankengine.pricing.controller;

import com.bankengine.catalog.dto.VersionRequest;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Pricing Component Management", description = "Operations for managing reusable pricing aggregates, including multi-dimensional Tiers and Price Values.")
@RestController
@RequestMapping("/api/v1/pricing-components")
@RequiredArgsConstructor
public class PricingComponentController {

    private final PricingComponentService pricingComponentService;

    @Operation(summary = "Retrieve all pricing components",
            description = "Returns a list of all reusable pricing definitions including nested tiers and price values. Tenant isolation is applied automatically.")
    @ApiResponse(responseCode = "200", description = "List of components successfully retrieved.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = PricingComponentResponse.class))))
    @GetMapping
    @PreAuthorize("hasAuthority('pricing:component:read')")
    public ResponseEntity<List<PricingComponentResponse>> getAllPricingComponents() {
        return ResponseEntity.ok(pricingComponentService.findAllComponents());
    }

    @Operation(summary = "Retrieve a pricing component by ID",
            description = "Fetches the full aggregate of a pricing component, including its tier configuration and associated conditions.")
    @ApiResponse(responseCode = "200", description = "Component successfully retrieved.")
    @ApiResponse(responseCode = "404", description = "Pricing component not found.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pricing:component:read')")
    public ResponseEntity<PricingComponentResponse> getPricingComponentById(
            @Parameter(description = "ID of the pricing component to retrieve", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(pricingComponentService.getComponentById(id));
    }

    @Operation(summary = "Define a new pricing aggregate",
            description = "Creates a new pricing component in DRAFT status. You may provide a full list of Tiers and Price Values in the initial request to create the aggregate at once.")
    @ApiResponse(responseCode = "201", description = "Pricing Component successfully created.",
            content = @Content(schema = @Schema(implementation = PricingComponentResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error or name conflict.")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions to create pricing.")
    @PostMapping
    @PreAuthorize("hasAuthority('pricing:component:create')")
    public ResponseEntity<PricingComponentResponse> createComponent(@Valid @RequestBody PricingComponentRequest requestDto) {
        return new ResponseEntity<>(pricingComponentService.createComponent(requestDto), HttpStatus.CREATED);
    }

    @Operation(summary = "Partial update of a DRAFT component (Aggregate Sync)",
            description = "Updates an existing DRAFT. Send only the fields to change. " +
                    "Updating the 'pricingTiers' list will synchronize all nested tiers, conditions, and prices. " +
                    "Only allowed while status is DRAFT.")
    @ApiResponse(responseCode = "200", description = "Pricing component aggregate successfully updated.")
    @ApiResponse(responseCode = "400", description = "Validation error or invalid tier logic.")
    @ApiResponse(responseCode = "403", description = "Modification blocked: Component is not in DRAFT status.")
    @ApiResponse(responseCode = "404", description = "Component not found.")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('pricing:component:update')")
    public ResponseEntity<PricingComponentResponse> patchPricingComponent(
            @Parameter(description = "ID of the pricing component to update", required = true)
            @PathVariable Long id,
            @RequestBody PricingComponentRequest requestDto) {
        return ResponseEntity.ok(pricingComponentService.updateComponent(id, requestDto));
    }

    @Operation(summary = "Version a pricing component",
            description = "Deep-clones a source component into a new DRAFT version. This allows evolving pricing (e.g., updating rates) without affecting products linked to historical versions.")
    @ApiResponse(responseCode = "201", description = "New pricing version successfully created.")
    @ApiResponse(responseCode = "404", description = "Source component not found.")
    @PostMapping("/{id}/version")
    @PreAuthorize("hasAuthority('pricing:component:create')")
    public ResponseEntity<Long> versionComponent(
            @Parameter(description = "ID of the component to serve as a template", required = true)
            @PathVariable Long id,
            @Valid @RequestBody VersionRequest requestDto) {
        return new ResponseEntity<>(pricingComponentService.versionComponent(id, requestDto), HttpStatus.CREATED);
    }

    @Operation(summary = "Activate a pricing component",
            description = "Transitions a DRAFT to ACTIVE. Once active, the pricing component can be linked to products and becomes immutable for direct updates.")
    @ApiResponse(responseCode = "200", description = "Component successfully activated.")
    @ApiResponse(responseCode = "400", description = "Activation failed (e.g., status is already ACTIVE).")
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('pricing:component:activate')")
    public ResponseEntity<Void> activateComponent(@PathVariable Long id) {
        pricingComponentService.activateComponent(id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Delete a pricing component (Dependency Checked)",
            description = "Deletes a pricing component. Fails with 409 Conflict if linked to products or if it contains active tiers.")
    @ApiResponse(responseCode = "204", description = "Component successfully deleted.")
    @ApiResponse(responseCode = "403", description = "Forbidden: Tenant mismatch.")
    @ApiResponse(responseCode = "404", description = "Not Found: Pricing component ID does not exist.")
    @ApiResponse(responseCode = "409", description = "Conflict: Component is in use by products or tiers.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('pricing:component:delete')")
    public void deletePricingComponent(
            @Parameter(description = "ID of the pricing component to delete", required = true)
            @PathVariable Long id) {
        pricingComponentService.deletePricingComponent(id);
    }
}