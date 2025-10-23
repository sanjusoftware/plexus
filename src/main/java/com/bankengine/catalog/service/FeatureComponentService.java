package com.bankengine.catalog.service;

import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.repository.FeatureComponentRepository;
import org.springframework.stereotype.Service;
import com.bankengine.catalog.dto.CreateFeatureComponentRequestDto;
import com.bankengine.catalog.dto.FeatureComponentResponseDto;
import com.bankengine.catalog.model.FeatureComponent.DataType;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FeatureComponentService {

    private final FeatureComponentRepository componentRepository;

    public FeatureComponentService(FeatureComponentRepository componentRepository) {
        this.componentRepository = componentRepository;
    }

    /**
     * Creates a new reusable Feature Component definition from a DTO.
     */
    public FeatureComponentResponseDto createFeature(CreateFeatureComponentRequestDto requestDto) {
        // 1. Validation (Unique name check is handled in the original logic)
        if (componentRepository.existsByName(requestDto.getName())) {
            throw new IllegalArgumentException("Feature Component name must be unique.");
        }

        // 2. Convert DTO to Entity
        FeatureComponent component = new FeatureComponent();
        component.setName(requestDto.getName());

        // Safely parse the String Data Type to the Enum
        try {
            component.setDataType(DataType.valueOf(requestDto.getDataType().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid data type provided: " + requestDto.getDataType());
        }

        // 3. Save and convert back to DTO
        FeatureComponent savedComponent = componentRepository.save(component);
        return convertToDto(savedComponent);
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
     * Retrieves all features and converts them to DTOs for external consumption.
     */
    public List<FeatureComponentResponseDto> getAllFeatures() {
        List<FeatureComponent> components = componentRepository.findAll();

        // Use Java Streams to map (convert) each entity to its corresponding DTO
        return components.stream()
                .map(this::convertToDto) // Use the helper method defined below
                .collect(Collectors.toList());
    }

    /**
     * Helper Method: Converts a FeatureComponent Entity to its Response DTO.
     */
    private FeatureComponentResponseDto convertToDto(FeatureComponent component) {
        FeatureComponentResponseDto dto = new FeatureComponentResponseDto();
        dto.setId(component.getId());
        dto.setName(component.getName());
        dto.setDataType(component.getDataType().name());
        return dto;
    }
}