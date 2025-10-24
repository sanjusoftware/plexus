package com.bankengine.pricing.controller;

import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.service.PricingCalculationService;
import org.springframework.http.HttpStatus;
import com.bankengine.pricing.repository.PricingComponentRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

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
                amount,
                component
        );
    }
}