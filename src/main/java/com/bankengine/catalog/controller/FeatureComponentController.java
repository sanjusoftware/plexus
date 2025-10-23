package com.bankengine.catalog.controller;

import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.service.FeatureComponentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.bankengine.catalog.dto.CreateFeatureComponentRequestDto;
import com.bankengine.catalog.dto.FeatureComponentResponseDto;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/v1/features")
public class FeatureComponentController {

    private final FeatureComponentService featureComponentService;

    public FeatureComponentController(FeatureComponentService featureComponentService) {
        this.featureComponentService = featureComponentService;
    }

    /**
     * POST /api/v1/features
     * Creates a new global, reusable feature component definition using a DTO.
     */
    @PostMapping
    public ResponseEntity<FeatureComponentResponseDto> createFeature(@Valid @RequestBody CreateFeatureComponentRequestDto requestDto) {
        try {
            // Service method now accepts DTO and returns DTO
            FeatureComponentResponseDto createdComponent = featureComponentService.createFeature(requestDto);
            return new ResponseEntity<>(createdComponent, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * GET /api/v1/features
     * Retrieves all defined feature components, returning a list of DTOs.
     */
    @GetMapping
    public ResponseEntity<List<FeatureComponentResponseDto>> getAllFeatures() {
        // Service method now returns List<FeatureComponentResponseDto>
        List<FeatureComponentResponseDto> features = featureComponentService.getAllFeatures();

        return new ResponseEntity<>(features, HttpStatus.OK);
    }
}