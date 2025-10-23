package com.bankengine.pricing.service;

import com.bankengine.pricing.dto.*;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.repository.PricingTierRepository;
import com.bankengine.pricing.repository.PriceValueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bankengine.pricing.model.PricingComponent.ComponentType;

import java.util.Optional;

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
        return dto;
    }

    /**
     * Retrieves a Pricing Component by ID.
     */
    public Optional<PricingComponent> getComponentById(Long id) {
        return componentRepository.findById(id);
    }

    /**
     * Links a new Tier and its Price Value (from DTOs) to an existing Pricing Component.
     */
    @Transactional
    public PriceValueResponseDto addTierAndValue(
            Long componentId,
            CreatePricingTierRequestDto tierDto,
            CreatePriceValueRequestDto valueDto) {
        // 1. Validate the component exists (unchanged)
        PricingComponent component = componentRepository.findById(componentId)
                .orElseThrow(() -> new IllegalArgumentException("Pricing Component not found."));

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