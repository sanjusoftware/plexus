package com.bankengine.pricing.service;

import com.bankengine.pricing.dto.*;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingComponent.ComponentType;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.repository.PriceValueRepository;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.repository.PricingTierRepository;
import com.bankengine.web.exception.DependencyViolationException;
import com.bankengine.web.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PricingComponentService {

    private final PricingComponentRepository componentRepository;
    private final PricingTierRepository tierRepository;
    private final PriceValueRepository valueRepository;

    public PricingComponentService(
            PricingComponentRepository componentRepository,
            PricingTierRepository tierRepository,
            PriceValueRepository valueRepository) {
        this.componentRepository = componentRepository;
        this.tierRepository = tierRepository;
        this.valueRepository = valueRepository;
    }

    /**
     * Helper method to retrieve a PricingComponent entity by ID, throwing NotFoundException on failure (404).
     */
    public PricingComponent getPricingComponentById(Long id) {
        return componentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pricing Component not found with ID: " + id));
    }

    /**
     * Creates a new global Pricing Component (e.g., 'Annual Fee') from a DTO.
     */
    @Transactional
    public PricingComponentResponseDto createComponent(CreatePricingComponentRequestDto requestDto) {
        // 1. Convert DTO to Entity
        PricingComponent component = new PricingComponent();
        component.setName(requestDto.getName());

        // Safely parse the String Type to the Enum
        try {
            component.setType(ComponentType.valueOf(requestDto.getType().toUpperCase()));
        } catch (IllegalArgumentException e) {
            // This is a 400 Bad Request
            throw new IllegalArgumentException("Invalid component type provided: " + requestDto.getType());
        }

        // 2. Save and convert back to DTO
        PricingComponent savedComponent = componentRepository.save(component);
        return convertToDto(savedComponent);
    }

    // New Helper method
    private PricingComponentResponseDto convertToDto(PricingComponent component) {
        PricingComponentResponseDto dto = new PricingComponentResponseDto();
        dto.setId(component.getId());
        dto.setName(component.getName());
        dto.setType(component.getType().name());
        dto.setCreatedAt(component.getCreatedAt());
        dto.setUpdatedAt(component.getUpdatedAt());
        return dto;
    }

    /**
     * Retrieves all Pricing Components.
     */
    @Transactional(readOnly = true)
    public List<PricingComponentResponseDto> findAllComponents() {
        return componentRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a single Pricing Component and converts it to a DTO.
     */
    @Transactional(readOnly = true)
    public PricingComponentResponseDto getComponentResponseById(Long id) {
        PricingComponent component = getPricingComponentById(id);
        return convertToDto(component);
    }

    // ... existing createComponent method remains the same ...

    /**
     * Updates an existing Pricing Component.
     */
    @Transactional
    public PricingComponentResponseDto updateComponent(Long id, UpdatePricingComponentRequestDto requestDto) {
        // 1. Validate component exists (handles 404)
        PricingComponent component = getPricingComponentById(id);

        // 2. Apply updates
        component.setName(requestDto.getName());

        try {
            component.setType(ComponentType.valueOf(requestDto.getType().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid component type provided: " + requestDto.getType());
        }

        // 3. Save and convert
        PricingComponent updatedComponent = componentRepository.save(component);
        // Note: JPA Auditing will automatically set the updatedAt timestamp.
        return convertToDto(updatedComponent);
    }

    /**
     * Deletes a Pricing Component by ID after checking for dependencies (PricingTier).
     */
    @Transactional
    public void deleteComponent(Long id) {
        // 1. Validate component exists (handles 404)
        PricingComponent component = getPricingComponentById(id);

        // 2. Dependency Check against PricingTier
        // A component cannot be deleted if it has tiers linked to it.
        // Assuming PricingTierRepository has a method: existsByPricingComponent_Id(Long componentId)
        boolean hasTiers = tierRepository.existsByPricingComponentId(id);

        if (hasTiers) {
            long tierCount = tierRepository.countByPricingComponentId(id);
            String message = String.format(
                    "Cannot delete Pricing Component ID %d: It has %d associated pricing tiers. Remove all tiers first.",
                    id, tierCount
            );
            // Throws exception, mapped to 409 Conflict by GlobalExceptionHandler
            throw new DependencyViolationException(message);
        }

        // 3. Perform deletion if no dependencies are found
        componentRepository.delete(component);
    }

    /**
     * Links a new Tier and its Price Value (from DTOs) to an existing Pricing Component.
     */
    @Transactional
    public PriceValueResponseDto addTierAndValue(
            Long componentId,
            CreatePricingTierRequestDto tierDto,
            CreatePriceValueRequestDto valueDto) {

        // 1. Validate the component exists (UPDATED to use centralized lookup)
        PricingComponent component = getPricingComponentById(componentId);

        // 2. Convert Tier DTO to Entity
        PricingTier tier = convertTierDtoToEntity(tierDto);
        tier.setPricingComponent(component);
        PricingTier savedTier = tierRepository.save(tier);

        // 3. Convert Value DTO to Entity
        PriceValue value = convertValueDtoToEntity(valueDto);
        value.setPricingTier(savedTier);
        PriceValue savedValue = valueRepository.save(value);

        // 4. Convert saved PriceValue Entity to Response DTO
        return convertValueEntityToResponseDto(savedValue);
    }

    private PricingTier convertTierDtoToEntity(CreatePricingTierRequestDto dto) {
        PricingTier tier = new PricingTier();
        tier.setTierName(dto.getTierName());
        tier.setConditionKey(dto.getConditionKey());
        tier.setConditionValue(dto.getConditionValue());
        tier.setMinThreshold(dto.getMinThreshold());
        tier.setMaxThreshold(dto.getMaxThreshold());
        return tier;
    }

    private PriceValue convertValueDtoToEntity(CreatePriceValueRequestDto dto) {
        PriceValue value = new PriceValue();
        value.setPriceAmount(dto.getPriceAmount());
        value.setCurrency(dto.getCurrency());

        try {
            value.setValueType(PriceValue.ValueType.valueOf(dto.getValueType().toUpperCase()));
        } catch (IllegalArgumentException e) {
            // This is a 400 Bad Request
            throw new IllegalArgumentException("Invalid value type provided: " + dto.getValueType());
        }
        return value;
    }

    private PriceValueResponseDto convertValueEntityToResponseDto(PriceValue entity) {
        PriceValueResponseDto dto = new PriceValueResponseDto();
        dto.setId(entity.getId());
        dto.setPricingTierId(entity.getPricingTier().getId());
        dto.setPriceAmount(entity.getPriceAmount());
        dto.setCurrency(entity.getCurrency());
        dto.setValueType(entity.getValueType().name());
        return dto;
    }
}