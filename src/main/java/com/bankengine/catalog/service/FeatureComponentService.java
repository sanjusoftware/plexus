package com.bankengine.catalog.service;

import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.repository.FeatureComponentRepository;
import com.bankengine.web.exception.NotFoundException; // 💡 NEW IMPORT
import org.springframework.stereotype.Service;
import com.bankengine.catalog.dto.CreateFeatureComponentRequestDto;
import com.bankengine.catalog.dto.FeatureComponentResponseDto;
import com.bankengine.catalog.model.FeatureComponent.DataType;

import java.util.List;
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
        // 1. Validation (Unique name check)
        if (componentRepository.existsByName(requestDto.getName())) {
            throw new IllegalArgumentException("Feature Component name must be unique.");
        }

        // 2. Convert DTO to Entity and parse DataType
        FeatureComponent component = new FeatureComponent();
        component.setName(requestDto.getName());

        try {
            component.setDataType(DataType.valueOf(requestDto.getDataType().toUpperCase()));
        } catch (IllegalArgumentException e) {
            // This is still a client error (bad data type string) -> 400 Bad Request
            throw new IllegalArgumentException("Invalid data type provided: " + requestDto.getDataType());
        }

        // 3. Save and convert back to DTO
        FeatureComponent savedComponent = componentRepository.save(component);
        return convertToDto(savedComponent);
    }

    /**
     * Retrieves a Feature Component by its ID, throwing NotFoundException if absent.
     * 💡 Updated to throw NotFoundException instead of returning Optional.
     */
    public FeatureComponent getFeatureComponentById(Long id) {
        return componentRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Feature Component not found with ID: " + id));
    }

    /**
     * Retrieves all features and converts them to DTOs for external consumption.
     */
    public List<FeatureComponentResponseDto> getAllFeatures() {
        List<FeatureComponent> components = componentRepository.findAll();

        return components.stream()
                .map(this::convertToDto)
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