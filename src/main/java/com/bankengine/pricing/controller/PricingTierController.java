package com.bankengine.pricing.controller;

import com.bankengine.pricing.dto.PriceValueResponseDto;
import com.bankengine.pricing.dto.TierValueDto;
import com.bankengine.pricing.dto.UpdateTierValueDto;
import com.bankengine.pricing.service.PricingComponentService;
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

@Tag(name = "Pricing Tier Management", description = "Manages conditional tiers and associated price values within a Pricing Component.")
@RestController
@RequestMapping("/api/v1/pricing-components/{componentId}/tiers")
public class PricingTierController {

    private final PricingComponentService pricingComponentService;

    public PricingTierController(PricingComponentService pricingComponentService) {
        this.pricingComponentService = pricingComponentService;
    }

    /**
     * POST /api/v1/pricing-components/{componentId}/tiers
     * Adds a Tier and its Value to an existing Pricing Component.
     */
    @Operation(summary = "Add a new pricing tier and value to a component",
            description = "Defines a PricingTier and its associated PriceValue (e.g., condition, threshold, and amount) under a specified component.")
    @ApiResponse(responseCode = "201", description = "Pricing Tier and Value successfully created and linked.",
            content = @Content(schema = @Schema(implementation = PriceValueResponseDto.class)))
    @ApiResponse(responseCode = "400", description = "Invalid data (e.g., componentId not found, invalid threshold values).")
    @PostMapping
    @PreAuthorize("hasAuthority('pricing:tier:create')")
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

    /**
     * PUT /api/v1/pricing-components/{componentId}/tiers/{tierId}
     * Updates an existing tier and its value.
     */
    @Operation(summary = "Update an existing pricing tier and its value",
            description = "Modifies the condition, thresholds (tier) and/or the amount/type (value) of an existing tier.")
    @ApiResponse(responseCode = "200", description = "Pricing Tier and Value successfully updated.",
            content = @Content(schema = @Schema(implementation = PriceValueResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "Pricing Component or Tier not found.")
    @PutMapping("/{tierId}")
    @PreAuthorize("hasAuthority('pricing:tier:update')")
    public ResponseEntity<PriceValueResponseDto> updateTieredPricing(
            @Parameter(description = "ID of the existing Pricing Component (for context).", required = true)
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

    /**
     * DELETE /api/v1/pricing-components/{componentId}/tiers/{tierId}
     * Deletes a specific pricing tier and its value.
     */
    @Operation(summary = "Delete a specific pricing tier and its value",
            description = "Removes a specific Pricing Tier and its associated Price Value.")
    @ApiResponse(responseCode = "204", description = "Pricing Tier and Value successfully deleted (No Content).")
    @ApiResponse(responseCode = "404", description = "Pricing Component or Tier not found.")
    @DeleteMapping("/{tierId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('pricing:tier:delete')")
    public void deleteTieredPricing(
            @Parameter(description = "ID of the existing Pricing Component (for context).", required = true)
            @PathVariable Long componentId,
            @Parameter(description = "ID of the Pricing Tier to delete.", required = true)
            @PathVariable Long tierId) {

        pricingComponentService.deleteTierAndValue(componentId, tierId);
    }
}