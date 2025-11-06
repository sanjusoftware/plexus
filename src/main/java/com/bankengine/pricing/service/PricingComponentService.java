package com.bankengine.pricing.service;

import com.bankengine.pricing.converter.PriceValueMapper;
import com.bankengine.pricing.converter.PricingComponentMapper;
import com.bankengine.pricing.converter.PricingTierMapper;
import com.bankengine.pricing.converter.TierConditionMapper;
import com.bankengine.pricing.dto.*;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingComponent.ComponentType;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.TierCondition;
import com.bankengine.pricing.repository.*;
import com.bankengine.rules.service.KieContainerReloadService;
import com.bankengine.web.exception.DependencyViolationException;
import com.bankengine.web.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PricingComponentService {

    private final PricingComponentRepository componentRepository;
    private final PricingTierRepository tierRepository;
    private final PriceValueRepository valueRepository;
    private final TierConditionRepository tierConditionRepository;
    private final PricingComponentMapper pricingComponentMapper;
    private final PricingTierMapper pricingTierMapper;
    private final PriceValueMapper priceValueMapper;
    private final TierConditionMapper tierConditionMapper;
    private final ProductPricingLinkRepository productPricingLinkRepository;
    private final KieContainerReloadService reloadService;

    public PricingComponentService(
            PricingComponentRepository componentRepository,
            PricingTierRepository tierRepository,
            PriceValueRepository valueRepository,
            TierConditionRepository tierConditionRepository,
            PricingComponentMapper pricingComponentMapper,
            PricingTierMapper pricingTierMapper,
            PriceValueMapper priceValueMapper,
            TierConditionMapper tierConditionMapper,
            ProductPricingLinkRepository productPricingLinkRepository,
            KieContainerReloadService reloadService) {
        this.componentRepository = componentRepository;
        this.tierRepository = tierRepository;
        this.valueRepository = valueRepository;
        this.tierConditionRepository = tierConditionRepository;
        this.pricingComponentMapper = pricingComponentMapper;
        this.pricingTierMapper = pricingTierMapper;
        this.priceValueMapper = priceValueMapper;
        this.tierConditionMapper = tierConditionMapper;
        this.productPricingLinkRepository = productPricingLinkRepository;
        this.reloadService = reloadService;
    }

    /**
     * Retrieves a PricingTier entity by ID, throwing NotFoundException on failure (404).
     */
    public PricingTier getPricingTierById(Long id) {
        return tierRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pricing Tier not found with ID: " + id));
    }

    /**
     * Updates an existing Pricing Tier and its associated Price Value, including conditions.
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

        // 4. Apply Tier Updates (including minThreshold/maxThreshold)
        pricingTierMapper.updateFromDto(dto.getTier(), tier);

        // 5. CRITICAL FIX: Update Tier Conditions (Full Replacement Logic)
        updateTierConditions(tier, dto.getTier().getConditions());

        // 6. Apply Value Updates
        priceValueMapper.updateFromDto(dto.getValue(), value);

        try {
            value.setValueType(PriceValue.ValueType.valueOf(dto.getValue().getValueType().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value type provided: " + dto.getValue().getValueType());
        }

        // 7. Save and Reload
        tierRepository.save(tier); // Saves tier and cascades saves/deletes for conditions/values due to CascadeType.ALL
        PriceValue savedValue = valueRepository.save(value); // Explicitly save value since it's used for the return DTO

        reloadService.reloadKieContainer();

        // 8. Convert saved PriceValue Entity to Response DTO
        return priceValueMapper.toResponseDto(savedValue);
    }

    // 💡 NEW HELPER METHOD FOR CLEANUP
    private void updateTierConditions(PricingTier tier, List<TierConditionDto> conditionDtos) {
        // Clear the old collection first (Hibernate will delete old entities due to CascadeType.ALL)
        tier.getConditions().clear();

        if (conditionDtos != null && !conditionDtos.isEmpty()) {
            Set<TierCondition> newConditions = conditionDtos.stream()
                .map(dto -> {
                    TierCondition condition = tierConditionMapper.toEntity(dto); // 💡 Use the new mapper
                    // CRITICAL: Set the mandatory bidirectional link
                    condition.setPricingTier(tier);
                    return condition;
                })
                .collect(Collectors.toSet());

            // Add the new collection (Hibernate will insert new entities)
            tier.getConditions().addAll(newConditions);
        }
    }

    /**
     * Deletes a Pricing Tier and its associated Price Value.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteTierAndValue(Long componentId, Long tierId) {
        getPricingComponentById(componentId);
        getTierById(tierId);
        tierConditionRepository.deleteByPricingTierId(tierId);
        valueRepository.deleteByPricingTierId(tierId);
        tierRepository.deleteById(tierId);
        reloadService.reloadKieContainer();
    }

    private void getTierById(Long tierId) {
        tierRepository.findById(tierId)
                .orElseThrow(() -> new NotFoundException("Pricing Tier not found with ID: " + tierId));
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
        reloadService.reloadKieContainer();
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
        reloadService.reloadKieContainer();
    }

    /**
     * Links a new Tier and its Price Value (from DTOs) to an existing Pricing Component.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PriceValueResponseDto addTierAndValue(
            Long componentId,
            CreatePricingTierRequestDto tierDto,
            CreatePriceValueRequestDto valueDto) {

        // 1. Validate the component exists
        PricingComponent component = getPricingComponentById(componentId);

        // 2. Convert Tier DTO to Entity
        PricingTier tier = pricingTierMapper.toEntity(tierDto);
        tier.setPricingComponent(component);

        // 3. Convert Value DTO to Entity
        PriceValue value = priceValueMapper.toEntity(valueDto);

        try {
            value.setValueType(PriceValue.ValueType.valueOf(valueDto.getValueType().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value type provided: " + valueDto.getValueType());
        }

        value.setPricingTier(tier);
        tier.setPriceValues(Set.of(value));

        // 4. CRITICAL: Handle Conditions (Map DTOs to Entities and link)
        if (tierDto.getConditions() != null && !tierDto.getConditions().isEmpty()) {
            Set<TierCondition> conditions = tierDto.getConditions().stream()
                    .map(dto -> {
                        TierCondition condition = tierConditionMapper.toEntity(dto); // 💡 Use the new mapper

                        // Set relationship back to the parent tier
                        condition.setPricingTier(tier);
                        return condition;
                    })
                    .collect(Collectors.toSet());

            tier.setConditions(conditions);
        }

        // 5. Save the Tier (This should cascade save the PriceValue and Conditions)
        PricingTier savedTier = tierRepository.save(tier);

        // 6. Retrieve the saved PriceValue for the response (assuming it was saved via cascade)
        PriceValue savedValue = valueRepository.findByPricingTierId(savedTier.getId())
                .orElseThrow(() -> new RuntimeException("PriceValue not found after save."));

        // 7. Reload KieContainer
        reloadService.reloadKieContainer();

        // 8. Convert saved PriceValue Entity to Response DTO
        return priceValueMapper.toResponseDto(savedValue);
    }
}