package com.bankengine.catalog.controller;

import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.service.FeatureComponentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/features")
public class FeatureComponentController {

    private final FeatureComponentService featureComponentService;

    public FeatureComponentController(FeatureComponentService featureComponentService) {
        this.featureComponentService = featureComponentService;
    }

    /**
     * POST /api/v1/features
     * Creates a new global, reusable feature component definition.
     */
    @PostMapping
    public ResponseEntity<FeatureComponent> createFeature(@RequestBody FeatureComponent component) {
        try {
            FeatureComponent createdComponent = featureComponentService.createFeatureComponent(component);
            return new ResponseEntity<>(createdComponent, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    // GET endpoint to retrieve all features
    @GetMapping
    public ResponseEntity<Iterable<FeatureComponent>> getAllFeatures() {
        return new ResponseEntity<>(featureComponentService.getAllFeatures(), HttpStatus.OK);
    }
}