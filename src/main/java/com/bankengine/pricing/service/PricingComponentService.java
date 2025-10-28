package com.bankengine.pricing.service;

import com.bankengine.pricing.converter.PriceValueMapper;
import com.bankengine.pricing.converter.PricingComponentMapper;
import com.bankengine.pricing.converter.PricingTierMapper;
import com.bankengine.pricing.dto.*;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingComponent.ComponentType;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.repository.PriceValueRepository;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.repository.PricingTierRepository;
import com.bankengine.pricing.repository.ProductPricingLinkRepository;
import com.bankengine.web.exception.DependencyViolationException;
import com.bankengine.web.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PricingComponentService {

    private final PricingComponentRepository componentRepository;
    private final PricingTierRepository tierRepository;
    private final PriceValueRepository valueRepository;
    private final PricingComponentMapper pricingComponentMapper;
    private final PricingTierMapper pricingTierMapper;
    private final PriceValueMapper priceValueMapper;
    private final ProductPricingLinkRepository productPricingLinkRepository;

    public PricingComponentService(
            PricingComponentRepository componentRepository,
            PricingTierRepository tierRepository,
            PriceValueRepository valueRepository,
            PricingComponentMapper pricingComponentMapper,
            PricingTierMapper pricingTierMapper,
            PriceValueMapper priceValueMapper, ProductPricingLinkRepository productPricingLinkRepository) {
        this.componentRepository = componentRepository;
        this.tierRepository = tierRepository;
        this.valueRepository = valueRepository;
        this.pricingComponentMapper = pricingComponentMapper;
        this.pricingTierMapper = pricingTierMapper;
        this.priceValueMapper = priceValueMapper;
        this.productPricingLinkRepository = productPricingLinkRepository;
    }

    /**
     * Retrieves a PricingTier entity by ID, throwing NotFoundException on failure (404).
     */
    public PricingTier getPricingTierById(Long id) {
        return tierRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pricing Tier not found with ID: " + id));
    }

    /**
     * Updates an existing Pricing Tier and its associated Price Value.
     */
    @Transactional
    public PriceValueResponseDto updateTierAndValue(
            Long componentId,
            Long tierId,
            UpdateTierValueDto dto) {

        // 1. Validation - Ensures the tier is under the correct component scope
        getPricingComponentById(componentId);

        // 2. Retrieve Tier (which also validates Tier existence)
        PricingTier tier = getPricingTierById(tierId);

        // 3. Retrieve Value (since it's a 1:1 relationship, we retrieve it via the Tier)
        PriceValue value = valueRepository.findByPricingTierId(tierId)
                .orElseThrow(() -> new NotFoundException("Price Value not found for Tier ID: " + tierId));

        // 4. Apply Tier Updates
        pricingTierMapper.updateFromDto(dto.getTier(), tier);
        PricingTier savedTier = tierRepository.save(tier);

        // 5. Apply Value Updates
        priceValueMapper.updateFromDto(dto.getValue(), value);

        try {
            value.setValueType(PriceValue.ValueType.valueOf(dto.getValue().getValueType().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value type provided: " + dto.getValue().getValueType());
        }
        PriceValue savedValue = valueRepository.save(value);

        // 6. Convert saved PriceValue Entity to Response DTO
        return priceValueMapper.toResponseDto(savedValue);
    }

    /**
     * Deletes a Pricing Tier and its associated Price Value.
     */
    @Transactional
    public void deleteTierAndValue(Long componentId, Long tierId) {
        getPricingComponentById(componentId);
        PricingTier tier = getPricingTierById(tierId);
        valueRepository.deleteByPricingTierId(tierId);
        tierRepository.delete(tier);
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
        PricingComponent component = pricingComponentMapper.toEntity(requestDto);

        try {
            component.setType(ComponentType.valueOf(requestDto.getType().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid component type provided: " + requestDto.getType());
        }

        // 2. Save and convert back to DTO
        PricingComponent savedComponent = componentRepository.save(component);
        return pricingComponentMapper.toResponseDto(savedComponent);
    }

    /**
     * Retrieves all Pricing Components.
     */
    @Transactional(readOnly = true)
    public List<PricingComponentResponseDto> findAllComponents() {
        List<PricingComponent> components = componentRepository.findAll();
        return pricingComponentMapper.toResponseDtoList(components);
    }

    /**
     * Retrieves a single Pricing Component and converts it to a DTO.
     */
    @Transactional(readOnly = true)
    public PricingComponentResponseDto getComponentById(Long id) {
        PricingComponent component = componentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pricing Component not found with ID: " + id));
        return pricingComponentMapper.toResponseDto(component);
    }

    /**
     * Updates an existing Pricing Component.
     */
    @Transactional
    public PricingComponentResponseDto updateComponent(Long id, UpdatePricingComponentRequestDto requestDto) {
        // 1. Validate component exists (handles 404)
        PricingComponent component = getPricingComponentById(id);

        // 2. Apply updates
        pricingComponentMapper.updateFromDto(requestDto, component);

        // Enum conversion remains in service for explicit validation/error handling
        try {
            component.setType(ComponentType.valueOf(requestDto.getType().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid component type provided: " + requestDto.getType());
        }

        // 3. Save and convert
        PricingComponent updatedComponent = componentRepository.save(component);
        return pricingComponentMapper.toResponseDto(updatedComponent);
    }

    /**
     * Deletes a Pricing Component by ID after checking for dependencies (PricingTier AND ProductPricingLink).
     */
    @Transactional
    public void deletePricingComponent(Long id) {
        // Validate component exists (handles 404)
        PricingComponent component = getPricingComponentById(id);

        // 1. Dependency Check on Pricing Tiers (Simplified)
        long tierCount = tierRepository.countByPricingComponentId(id);

        if (tierCount > 0) {
            String message = String.format(
                    "Cannot delete Pricing Component ID %d ('%s'): It has %d associated pricing tiers. Remove all tiers first.",
                    id, component.getName(), tierCount
            );
            throw new DependencyViolationException(message);
        }

        // 2. Dependency Check on Product Links (Simplified)
        long linkCount = productPricingLinkRepository.countByPricingComponentId(id);

        if (linkCount > 0) {
            String message = String.format(
                    "Cannot delete Pricing Component ID %d ('%s'): It is currently linked to %d active product(s). Unlink all product dependencies first.",
                    id, component.getName(), linkCount
            );
            // Throws exception, which the GlobalExceptionHandler maps to 409 Conflict
            throw new DependencyViolationException(message);
        }

        // 3. Perform deletion
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

        // 1. Validate the component exists
        PricingComponent component = getPricingComponentById(componentId);

        // 2. Convert Tier DTO to Entity
        PricingTier tier = pricingTierMapper.toEntity(tierDto);
        tier.setPricingComponent(component);
        PricingTier savedTier = tierRepository.save(tier);

        // 3. Convert Value DTO to Entity
        PriceValue value = priceValueMapper.toEntity(valueDto);

        try {
            value.setValueType(PriceValue.ValueType.valueOf(valueDto.getValueType().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value type provided: " + valueDto.getValueType());
        }

        value.setPricingTier(savedTier);
        PriceValue savedValue = valueRepository.save(value);

        // 4. Convert saved PriceValue Entity to Response DTO
        return priceValueMapper.toResponseDto(savedValue);
    }
}