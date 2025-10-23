package com.bankengine.pricing.controller;

import com.bankengine.pricing.dto.TierValueDto;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.service.PricingComponentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pricing-components")
public class PricingComponentController {

    private final PricingComponentService pricingComponentService;

    public PricingComponentController(PricingComponentService pricingComponentService) {
        this.pricingComponentService = pricingComponentService;
    }

    /**
     * POST /api/v1/pricing-components
     * Defines a new global pricing component (e.g., 'FX Fee').
     */
    @PostMapping
    public ResponseEntity<PricingComponent> createComponent(@RequestBody PricingComponent component) {
        PricingComponent createdComponent = pricingComponentService.createComponent(component);
        return new ResponseEntity<>(createdComponent, HttpStatus.CREATED);
    }

    /**
     * POST /api/v1/pricing-components/{componentId}/tiers
     * Adds a Tier and its Value to an existing Pricing Component.
     */
    @PostMapping("/{componentId}/tiers")
    public ResponseEntity<PriceValue> addTieredPricing(
            @PathVariable Long componentId,
            @RequestBody TierValueDto dto) {

        try {
            PriceValue priceValue = pricingComponentService.addTierAndValue(
                    componentId,
                    dto.getTier(),
                    dto.getPriceValue());

            return new ResponseEntity<>(priceValue, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity(e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }
}