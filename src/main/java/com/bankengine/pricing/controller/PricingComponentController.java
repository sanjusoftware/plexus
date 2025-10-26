package com.bankengine.pricing.controller;

import com.bankengine.pricing.dto.*;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Pricing Component Management", description = "Manages definitions of reusable pricing items (e.g., Annual Fee, Interest Rate, Service Charge).")
@RestController
@RequestMapping("/api/v1/pricing-components")
public class PricingComponentController {

    private final PricingComponentService pricingComponentService;

    public PricingComponentController(PricingComponentService pricingComponentService) {
        this.pricingComponentService = pricingComponentService;
    }

    @Operation(summary = "Retrieve all pricing components",
            description = "Returns a list of all reusable pricing component definitions.")
    @ApiResponse(responseCode = "200", description = "List of components successfully retrieved.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = PricingComponentResponseDto.class))))
    @GetMapping
    public ResponseEntity<List<PricingComponentResponseDto>> getAllComponents() {
        List<PricingComponentResponseDto> components = pricingComponentService.findAllComponents();
        return new ResponseEntity<>(components, HttpStatus.OK);
    }

    @Operation(summary = "Retrieve a pricing component by ID",
            description = "Fetches the details of a specific pricing component.")
    @ApiResponse(responseCode = "200", description = "Component successfully retrieved.")
    @ApiResponse(responseCode = "404", description = "Component not found.")
    @GetMapping("/{id}")
    public ResponseEntity<PricingComponentResponseDto> getComponentById(
            @Parameter(description = "ID of the pricing component", required = true)
            @PathVariable Long id) {
        PricingComponentResponseDto responseDto = pricingComponentService.getComponentResponseById(id);
        return new ResponseEntity<>(responseDto, HttpStatus.OK);
    }

    /**
     * POST /api/v1/pricing-components
     * Defines a new global pricing component (e.g., 'FX Fee') using DTO and validation.
     */
    @Operation(summary = "Define a new global pricing component",
               description = "Creates the high-level pricing definition (e.g., 'FX Fee') before tiers are added.")
    @ApiResponse(responseCode = "201", description = "Pricing Component successfully created.",
                 content = @Content(schema = @Schema(implementation = PricingComponentResponseDto.class)))
    @ApiResponse(responseCode = "400", description = "Validation or business logic error (e.g., name conflict).")
    @PostMapping
    public ResponseEntity<PricingComponentResponseDto> createComponent(@Valid @RequestBody CreatePricingComponentRequestDto requestDto) {
        PricingComponentResponseDto createdComponent = pricingComponentService.createComponent(requestDto);
        return new ResponseEntity<>(createdComponent, HttpStatus.CREATED);
    }

    @Operation(summary = "Update an existing pricing component",
            description = "Updates the name and/or type of an existing pricing component.")
    @ApiResponse(responseCode = "200", description = "Pricing component successfully updated.",
            content = @Content(schema = @Schema(implementation = PricingComponentResponseDto.class)))
    @ApiResponse(responseCode = "400", description = "Validation or business logic error (e.g., invalid type).")
    @ApiResponse(responseCode = "404", description = "Component not found.")
    @PutMapping("/{id}")
    public ResponseEntity<PricingComponentResponseDto> updateComponent(
            @Parameter(description = "ID of the pricing component to update", required = true)
            @PathVariable Long id,
            @Valid @RequestBody UpdatePricingComponentRequestDto requestDto) {

        PricingComponentResponseDto updatedComponent = pricingComponentService.updateComponent(id, requestDto);
        return new ResponseEntity<>(updatedComponent, HttpStatus.OK);
    }

    @Operation(summary = "Delete a pricing component (Dependency Checked)",
            description = "Deletes a pricing component by its ID. Fails with 409 Conflict if associated pricing tiers exist.")
    @ApiResponse(responseCode = "204", description = "Component successfully deleted (No Content).")
    @ApiResponse(responseCode = "409", description = "Conflict: Component is linked to existing pricing tiers.")
    @ApiResponse(responseCode = "404", description = "Component not found.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComponent(
            @Parameter(description = "ID of the pricing component to delete", required = true)
            @PathVariable Long id) {
        pricingComponentService.deleteComponent(id);
    }

    /**
     * POST /api/v1/pricing-components/{componentId}/tiers
     * Adds a Tier and its Value to an existing Pricing Component using nested DTOs.
     */
    @Operation(summary = "Add a new pricing tier and value to a component",
               description = "Defines a PricingTier and its associated PriceValue (e.g., condition, threshold, and amount) under a specified component.")
    @ApiResponse(responseCode = "201", description = "Pricing Tier and Value successfully created and linked.",
                 content = @Content(schema = @Schema(implementation = PriceValueResponseDto.class)))
    @ApiResponse(responseCode = "400", description = "Invalid data (e.g., componentId not found, invalid threshold values).")
    @PostMapping("/{componentId}/tiers")
    public ResponseEntity<PriceValueResponseDto> addTieredPricing(
            @Parameter(description = "The ID of the existing Pricing Component.", required = true)
            @PathVariable Long componentId,
            @Valid @RequestBody TierValueDto dto) {
        PriceValueResponseDto responseDto = pricingComponentService.addTierAndValue(
                componentId,
                dto.getTier(),
                dto.getValue());
        return new ResponseEntity<>(responseDto, HttpStatus.CREATED);
    }

    // --- PUT /tiers/{tierId} ---

    @Operation(summary = "Update an existing pricing tier and its value",
            description = "Modifies the condition, thresholds (tier) and/or the amount/type (value) of an existing tier.")
    @ApiResponse(responseCode = "200", description = "Pricing Tier and Value successfully updated.",
            content = @Content(schema = @Schema(implementation = PriceValueResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "Pricing Component or Tier not found.")
    @PutMapping("/{componentId}/tiers/{tierId}")
    public ResponseEntity<PriceValueResponseDto> updateTieredPricing(
            @Parameter(description = "ID of the existing Pricing Component.", required = true)
            @PathVariable Long componentId,
            @Parameter(description = "ID of the existing Pricing Tier.", required = true)
            @PathVariable Long tierId,
            @Valid @RequestBody UpdateTierValueDto dto) {

        PriceValueResponseDto responseDto = pricingComponentService.updateTierAndValue(
                componentId,
                tierId,
                dto);
        return new ResponseEntity<>(responseDto, HttpStatus.OK);
    }

    // --- DELETE /tiers/{tierId} ---

    @Operation(summary = "Delete a specific pricing tier and its value",
            description = "Removes a specific Pricing Tier and its associated Price Value.")
    @ApiResponse(responseCode = "204", description = "Pricing Tier and Value successfully deleted (No Content).")
    @ApiResponse(responseCode = "404", description = "Pricing Component or Tier not found.")
    @DeleteMapping("/{componentId}/tiers/{tierId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTieredPricing(
            @Parameter(description = "ID of the existing Pricing Component.", required = true)
            @PathVariable Long componentId,
            @Parameter(description = "ID of the Pricing Tier to delete.", required = true)
            @PathVariable Long tierId) {

        pricingComponentService.deleteTierAndValue(componentId, tierId);
    }
}