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
     * Retrieves a PricingTier entity by ID.
     */
    public PricingTier getPricingTierById(Long id) {
        return tierRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pricing Tier not found with ID: " + id));
    }

    /**
     * Updates an existing Pricing Tier and its associated Price Value.
     */
    @Transactional
    public ProductPricingCalculationResult.PriceComponentDetail updateTierAndValue(
            Long componentId,
            Long tierId,
            UpdateTierValueDto dto) {

        getPricingComponentById(componentId);
        PricingTier tier = getPricingTierById(tierId);
        PriceValue value = valueRepository.findByPricingTierId(tierId)
                .orElseThrow(() -> new NotFoundException("Price Value not found for Tier ID: " + tierId));

        pricingTierMapper.updateFromDto(dto.getTier(), tier);
        updateTierConditions(tier, dto.getTier().getConditions());
        priceValueMapper.updateFromDto(dto.getValue(), value);

        try {
            value.setValueType(PriceValue.ValueType.valueOf(dto.getValue().getValueType().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value type provided: " + dto.getValue().getValueType());
        }

        tierRepository.save(tier);
        PriceValue savedValue = valueRepository.save(value);

        reloadService.reloadKieContainer();

        return priceValueMapper.toResponseDto(savedValue);
    }

    private void updateTierConditions(PricingTier tier, List<TierConditionDto> conditionDtos) {
        tier.getConditions().clear();
        if (conditionDtos != null && !conditionDtos.isEmpty()) {
            Set<TierCondition> newConditions = conditionDtos.stream()
                .map(dto -> {
                    TierCondition condition = tierConditionMapper.toEntity(dto);
                    condition.setPricingTier(tier);
                    return condition;
                })
                .collect(Collectors.toSet());
            tier.getConditions().addAll(newConditions);
        }
    }

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

    public PricingComponent getPricingComponentById(Long id) {
        return componentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pricing Component not found with ID: " + id));
    }

    @Transactional
    public PricingComponentResponse createComponent(PricingComponentRequest requestDto) {
        PricingComponent component = pricingComponentMapper.toEntity(requestDto);
        try {
            component.setType(ComponentType.valueOf(requestDto.getType().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid component type provided: " + requestDto.getType());
        }
        PricingComponent savedComponent = componentRepository.save(component);
        reloadService.reloadKieContainer();
        return pricingComponentMapper.toResponseDto(savedComponent);
    }

    @Transactional(readOnly = true)
    public List<PricingComponentResponse> findAllComponents() {
        List<PricingComponent> components = componentRepository.findAll();
        return pricingComponentMapper.toResponseDtoList(components);
    }

    @Transactional(readOnly = true)
    public PricingComponentResponse getComponentById(Long id) {
        PricingComponent component = getPricingComponentById(id);
        return pricingComponentMapper.toResponseDto(component);
    }

    @Transactional
    public PricingComponentResponse updateComponent(Long id, PricingComponentRequest requestDto) {
        PricingComponent component = getPricingComponentById(id);
        pricingComponentMapper.updateFromDto(requestDto, component);
        try {
            component.setType(ComponentType.valueOf(requestDto.getType().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid component type provided: " + requestDto.getType());
        }
        PricingComponent updatedComponent = componentRepository.save(component);
        return pricingComponentMapper.toResponseDto(updatedComponent);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deletePricingComponent(Long id) {
        PricingComponent component = getPricingComponentById(id);
        long tierCount = tierRepository.countByPricingComponentId(id);
        if (tierCount > 0) {
            throw new DependencyViolationException(String.format("Cannot delete component %d: association with %d tiers exists.", id, tierCount));
        }
        long linkCount = productPricingLinkRepository.countByPricingComponentId(id);
        if (linkCount > 0) {
            throw new DependencyViolationException(String.format("Cannot delete component %d: linked to %d products.", id, linkCount));
        }
        componentRepository.delete(component);
        reloadService.reloadKieContainer();
    }

    /**
     * Links a new Tier and its Price Value to an existing Pricing Component.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProductPricingCalculationResult.PriceComponentDetail addTierAndValue(
            Long componentId,
            PricingTierRequest tierDto,
            PriceValueRequest valueDto) {

        PricingComponent component = getPricingComponentById(componentId);
        PricingTier tier = pricingTierMapper.toEntity(tierDto);
        tier.setPricingComponent(component);
        PriceValue value = priceValueMapper.toEntity(valueDto);

        try {
            value.setValueType(PriceValue.ValueType.valueOf(valueDto.getValueType().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value type provided: " + valueDto.getValueType());
        }

        value.setPricingTier(tier);
        tier.setPriceValues(Set.of(value));

        if (tierDto.getConditions() != null && !tierDto.getConditions().isEmpty()) {
            Set<TierCondition> conditions = tierDto.getConditions().stream()
                    .map(dto -> {
                        TierCondition condition = tierConditionMapper.toEntity(dto);
                        condition.setPricingTier(tier);
                        return condition;
                    })
                    .collect(Collectors.toSet());
            tier.setConditions(conditions);
        }

        PricingTier savedTier = tierRepository.save(tier);
        PriceValue savedValue = valueRepository.findByPricingTierId(savedTier.getId())
                .orElseThrow(() -> new RuntimeException("PriceValue not found after save."));

        reloadService.reloadKieContainer();
        return priceValueMapper.toResponseDto(savedValue);
    }
}