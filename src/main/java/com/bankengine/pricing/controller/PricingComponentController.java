package com.bankengine.pricing.controller;

import com.bankengine.pricing.dto.CreatePricingComponentRequestDto;
import com.bankengine.pricing.dto.PriceValueResponseDto;
import com.bankengine.pricing.dto.PricingComponentResponseDto;
import com.bankengine.pricing.dto.TierValueDto;
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
import org.springframework.web.bind.annotation.*;

@Tag(name = "Pricing Component Management", description = "Manages definitions of reusable pricing items (e.g., Annual Fee, Interest Rate, Service Charge).")
@RestController
@RequestMapping("/api/v1/pricing-components")
public class PricingComponentController {

    private final PricingComponentService pricingComponentService;

    public PricingComponentController(PricingComponentService pricingComponentService) {
        this.pricingComponentService = pricingComponentService;
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
}