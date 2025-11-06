package com.bankengine.pricing.controller;

import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.service.PricingCalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@Tag(name = "Pricing Calculation", description = "Dynamic price retrieval using the Drools Rule Engine.")
@RestController
@RequestMapping("/api/v1/pricing")
public class PricingController {

    private final PricingCalculationService calculationService;
    private final PricingComponentRepository componentRepository;

    public PricingController(PricingCalculationService calculationService, PricingComponentRepository componentRepository) {
        this.calculationService = calculationService;
        this.componentRepository = componentRepository;
    }

    /**
     * GET /api/v1/pricing/calculate/1?segment=HNW&amount=500000
     * Calculates all pricing components for a product based on inputs.
     */
    @Operation(summary = "Calculate the final price/fee based on customer and transaction data",
            description = "Executes the Drools ruleset to determine the correct Pricing Tier and returns the associated PriceValue.",
            parameters = {
                    @Parameter(name = "componentId", description = "The ID of the Pricing Component (e.g., Annual Fee component ID)", required = true),
                    @Parameter(name = "segment", description = "The Customer Segment code (e.g., PREMIUM, STANDARD, HNW)", required = true),
                    @Parameter(name = "amount", description = "The transaction or account amount to be evaluated against tiers (e.g., loan size)", required = true)
            })
    @ApiResponse(responseCode = "200", description = "Successfully calculated and returned the PriceValue.",
            content = @Content(schema = @Schema(implementation = PriceValue.class)))
    @ApiResponse(responseCode = "404", description = "No matching pricing tier was found for the given inputs (Rule did not fire).")
    @GetMapping("/calculate/{componentId}")
    public PriceValue getCalculatedPrice(
            @PathVariable("componentId") Long componentId,
            @RequestParam String segment,
            @RequestParam BigDecimal amount) {

        // 1. Fetch the PricingComponent based on the ID
        PricingComponent component = componentRepository.findById(componentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Pricing Component not found with ID: " + componentId
                ));

        // 2. Return the final PriceValue object directly
        return calculationService.getCalculatedPrice(
                segment,
                amount
        );
    }
}