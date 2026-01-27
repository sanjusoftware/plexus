package com.bankengine.pricing.service;

import com.bankengine.common.service.BaseService;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PricingComponentService extends BaseService {

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
        return getByIdSecurely(tierRepository, id, "Pricing Tier");
    }

    /**
     * Updates an existing Pricing Tier and its associated Price Value.
     */
    @Transactional
    @CacheEvict(value = {"publicCatalog", "productDetails"}, allEntries = true)
    public ProductPricingCalculationResult.PriceComponentDetail updateTierAndValue(
            Long componentId,
            Long tierId,
            TieredPriceRequest dto) {

        getPricingComponentById(componentId);
        PricingTier tier = getPricingTierById(tierId);
        PriceValue value = valueRepository.findByPricingTierId(tierId)
                .orElseThrow(() -> new NotFoundException("Price Value not found for Tier ID: " + tierId));

        pricingTierMapper.updateFromDto(dto.getTier(), tier);
        updateTierConditions(tier, dto.getTier().getConditions());
        priceValueMapper.updateFromDto(dto.getValue(), value);

        setValueType(dto.getValue(), value);

        tierRepository.save(tier);
        PriceValue savedValue = valueRepository.save(value);

        reloadService.reloadKieContainer();

        return priceValueMapper.toDetailDto(savedValue);
    }

    private void updateTierConditions(PricingTier tier, List<TierConditionDto> conditionDtos) {
        tier.getConditions().clear();
        if (conditionDtos != null && !conditionDtos.isEmpty()) {
            Set<TierCondition> newConditions = conditionDtos.stream()
                .map(dto -> {
                    TierCondition condition = tierConditionMapper.toEntity(dto);
                    condition.setPricingTier(tier);
                    condition.setBankId(getCurrentBankId());
                    return condition;
                })
                .collect(Collectors.toSet());
            tier.getConditions().addAll(newConditions);
        }
    }

    @Transactional
    @CacheEvict(value = {"publicCatalog", "productDetails"}, allEntries = true)
    public void deleteTierAndValue(Long componentId, Long tierId) {
        getPricingComponentById(componentId);
        getPricingTierById(tierId);
        tierConditionRepository.deleteByPricingTierId(tierId);
        valueRepository.deleteByPricingTierId(tierId);
        tierRepository.deleteById(tierId);
        reloadService.reloadKieContainer();
    }

    public PricingComponent getPricingComponentById(Long id) {
        return getByIdSecurely(componentRepository, id, "Pricing Component");
    }

    @Transactional
    public PricingComponentResponse createComponent(PricingComponentRequest requestDto) {
        PricingComponent component = pricingComponentMapper.toEntity(requestDto);
        component.setBankId(getCurrentBankId());

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
    @CacheEvict(value = {"publicCatalog", "productDetails"}, allEntries = true)
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

    @Transactional
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
    @Transactional
    @CacheEvict(value = {"publicCatalog", "productDetails"}, allEntries = true)
    public ProductPricingCalculationResult.PriceComponentDetail addTierAndValue(
            Long componentId,
            PricingTierRequest tierDto,
            PriceValueRequest valueDto) {
        PricingComponent component = getPricingComponentById(componentId);
        String bankId = getCurrentBankId();

        PricingTier tier = pricingTierMapper.toEntity(tierDto);
        tier.setPricingComponent(component);
        tier.setBankId(bankId);

        PriceValue value = priceValueMapper.toEntity(valueDto);
        value.setBankId(bankId);

        setValueType(valueDto, value);

        value.setPricingTier(tier);
        tier.setPriceValues(Set.of(value));

        if (tierDto.getConditions() != null && !tierDto.getConditions().isEmpty()) {
            Set<TierCondition> conditions = tierDto.getConditions().stream()
                    .map(dto -> {
                        TierCondition condition = tierConditionMapper.toEntity(dto);
                        condition.setPricingTier(tier);
                        condition.setBankId(bankId);
                        return condition;
                    })
                    .collect(Collectors.toSet());
            tier.setConditions(conditions);
        }

        PricingTier savedTier = tierRepository.save(tier);
        Long tierId = savedTier.getId();
        PriceValue savedValue = valueRepository.findByPricingTierId(tierId)
                .orElseThrow(() -> new RuntimeException("Price Value not found for Tier: " + tierId));

        reloadService.reloadKieContainer();
        return priceValueMapper.toDetailDto(savedValue);
    }

    private static void setValueType(PriceValueRequest valueDto, PriceValue value) {
        try {
            value.setValueType(PriceValue.ValueType.valueOf(valueDto.getValueType().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value type provided: " + valueDto.getValueType());
        }
    }
}