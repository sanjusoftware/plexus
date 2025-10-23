package com.bankengine.pricing.controller;

import com.bankengine.pricing.dto.CreatePricingComponentRequestDto;
import com.bankengine.pricing.dto.PriceValueResponseDto;
import com.bankengine.pricing.dto.PricingComponentResponseDto;
import com.bankengine.pricing.dto.TierValueDto;
import com.bankengine.pricing.service.PricingComponentService;
import jakarta.validation.Valid;
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
     * Defines a new global pricing component (e.g., 'FX Fee') using DTO and validation.
     */
    @PostMapping
    public ResponseEntity<PricingComponentResponseDto> createComponent(@Valid @RequestBody CreatePricingComponentRequestDto requestDto) {
        // Service method now accepts DTO and returns DTO
        PricingComponentResponseDto createdComponent = pricingComponentService.createComponent(requestDto);
        return new ResponseEntity<>(createdComponent, HttpStatus.CREATED);
    }

    /**
     * POST /api/v1/pricing-components/{componentId}/tiers
     * Adds a Tier and its Value to an existing Pricing Component using nested DTOs.
     */
    @PostMapping("/{componentId}/tiers")
    public ResponseEntity<PriceValueResponseDto> addTieredPricing(@PathVariable Long componentId,
                                                                   @Valid @RequestBody TierValueDto dto) {
        try {
            // Service method is updated to accept the DTOs
            PriceValueResponseDto responseDto = pricingComponentService.addTierAndValue(
                    componentId,
                    dto.getTier(),
                    dto.getValue());

            return new ResponseEntity<>(responseDto, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            // Using the global handler is better, but this remains for business logic exceptions
            return new ResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}