package com.bankengine.pricing.controller;

import com.bankengine.pricing.dto.CalculatedPriceDto;
import com.bankengine.pricing.service.PricingCalculationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pricing")
public class PricingController {

    private final PricingCalculationService calculationService;

    public PricingController(PricingCalculationService calculationService) {
        this.calculationService = calculationService;
    }

    /**
     * GET /api/v1/pricing/calculate?productId=1&segment=HNW&amount=500000
     * Calculates all pricing components for a product based on inputs.
     */
    @GetMapping("/calculate")
    public ResponseEntity<List<CalculatedPriceDto>> calculatePrice(
            @RequestParam Long productId,
            @RequestParam String segment,
            @RequestParam BigDecimal amount) {

        List<CalculatedPriceDto> result = calculationService.calculateProductPrice(
                productId, segment, amount);

        if (result.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}