package com.bankengine.catalog.service;

import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.repository.FeatureComponentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FeatureComponentService {

    private final FeatureComponentRepository componentRepository;

    public FeatureComponentService(FeatureComponentRepository componentRepository) {
        this.componentRepository = componentRepository;
    }

    /**
     * Creates a new reusable Feature Component definition.
     */
    public FeatureComponent createFeatureComponent(FeatureComponent component) {
        // Simple validation: ensure the feature name is unique
        if (componentRepository.existsByName(component.getName())) {
            throw new IllegalArgumentException("Feature Component name must be unique.");
        }
        return componentRepository.save(component);
    }

    /**
     * Retrieves a Feature Component by its ID.
     */
    public Optional<FeatureComponent> getFeatureComponent(Long id) {
        return componentRepository.findById(id);
    }

    /**
     * Retrieves all defined Feature Components.
     */
    public List<FeatureComponent> getAllFeatures() {
        return componentRepository.findAll();
    }
}