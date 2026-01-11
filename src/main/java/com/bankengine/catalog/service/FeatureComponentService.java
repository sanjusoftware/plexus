package com.bankengine.catalog.service;

import com.bankengine.catalog.converter.FeatureComponentMapper;
import com.bankengine.catalog.dto.FeatureComponentRequest;
import com.bankengine.catalog.dto.FeatureComponentResponse;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.FeatureComponent.DataType;
import com.bankengine.catalog.repository.FeatureComponentRepository;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import com.bankengine.common.service.BaseService;
import com.bankengine.web.exception.DependencyViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FeatureComponentService extends BaseService {

    private final FeatureComponentRepository componentRepository;
    private final ProductFeatureLinkRepository linkRepository;
    private final FeatureComponentMapper featureComponentMapper;

    public FeatureComponentService(FeatureComponentRepository componentRepository, ProductFeatureLinkRepository linkRepository, FeatureComponentMapper featureComponentMapper) {
        this.componentRepository = componentRepository;
        this.linkRepository = linkRepository;
        this.featureComponentMapper = featureComponentMapper;
    }

    /**
     * Creates a new reusable Feature Component definition from a DTO.
     */
    public FeatureComponentResponse createFeature(FeatureComponentRequest requestDto) {
        // 1. Validation (Unique name check)
        if (componentRepository.existsByName(requestDto.getName())) {
            throw new IllegalArgumentException("Feature Component name must be unique.");
        }

        // 2. Convert DTO to Entity and parse DataType
        FeatureComponent component = featureComponentMapper.toEntity(requestDto);

        try {
            component.setBankId(getCurrentBankId());
            component.setDataType(DataType.valueOf(requestDto.getDataType().toUpperCase()));
        } catch (IllegalArgumentException e) {
            // This is still a client error (bad data type string) -> 400 Bad Request
            throw new IllegalArgumentException("Invalid data type provided: " + requestDto.getDataType());
        }

        // 3. Save and convert back to DTO
        FeatureComponent savedComponent = componentRepository.save(component);
        return featureComponentMapper.toResponseDto(savedComponent);
    }

    /**
     * Retrieves a Feature Component by its ID, throwing NotFoundException if absent.
     */
    public FeatureComponent getFeatureComponentById(Long id) {
        return getByIdSecurely(componentRepository, id, "Feature Component");
    }

    /**
     * Retrieves all features and converts them to DTOs for external consumption.
     */
    @Transactional(readOnly = true)
    public List<FeatureComponentResponse> getAllFeatures() {
        return componentRepository.findAll().stream()
                .map(featureComponentMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a single feature and converts it to a DTO.
     */
    @Transactional(readOnly = true)
    public FeatureComponentResponse getFeatureResponseById(Long id) {
        return featureComponentMapper.toResponseDto(getFeatureComponentById(id));
    }

    /**
     * Updates an existing Feature Component.
     */
    @Transactional
    public FeatureComponentResponse updateFeature(Long id, FeatureComponentRequest requestDto) {
        // 1. Validate component exists (handles 404)
        FeatureComponent component = getFeatureComponentById(id);

        // 2. Validation (Unique name check - ensure it's not the current component's name)
        if (!component.getName().equalsIgnoreCase(requestDto.getName()) && componentRepository.existsByName(requestDto.getName())) {
            throw new IllegalArgumentException("Feature Component name must be unique.");
        }

        // 3. Apply updates
        featureComponentMapper.updateFromDto(requestDto, component);

        try {
            component.setDataType(DataType.valueOf(requestDto.getDataType().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid data type provided: " + requestDto.getDataType());
        }

        // 4. Save and convert
        return featureComponentMapper.toResponseDto(componentRepository.save(component));
    }

    /**
     * Deletes a Feature Component by ID after checking for product dependencies.
     */
    @Transactional
    public void deleteFeature(Long id) {
        // 1. Validate component exists (handles 404)
        FeatureComponent component = getFeatureComponentById(id);

        // 2. Dependency Check
        // Assuming linkRepository has a method to check existence by featureComponentId
        boolean isLinked = linkRepository.existsByFeatureComponentId(id);

        if (isLinked) {
            long linkCount = linkRepository.countByFeatureComponentId(id);
            String message = String.format(
                    "Cannot delete Feature Component ID %d: It is currently linked to %d active product(s). Unlink all dependencies first.",
                    id, linkCount
            );
            // Throws exception, which the GlobalExceptionHandler maps to 409 Conflict
            throw new DependencyViolationException(message);
        }

        // 3. Perform deletion if no dependencies are found
        componentRepository.delete(component);
    }

}