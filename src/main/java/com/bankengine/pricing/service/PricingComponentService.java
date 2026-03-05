package com.bankengine.pricing.service;

import com.bankengine.catalog.dto.VersionRequest;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.common.service.BaseService;
import com.bankengine.pricing.converter.PriceValueMapper;
import com.bankengine.pricing.converter.PricingComponentMapper;
import com.bankengine.pricing.converter.PricingTierMapper;
import com.bankengine.pricing.converter.TierConditionMapper;
import com.bankengine.pricing.dto.*;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.TierCondition;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.repository.PricingTierRepository;
import com.bankengine.pricing.repository.ProductPricingLinkRepository;
import com.bankengine.rules.service.KieContainerReloadService;
import com.bankengine.web.exception.DependencyViolationException;
import com.bankengine.web.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PricingComponentService extends BaseService {

    private final PricingComponentRepository pricingComponentRepository;
    private final PricingTierRepository tierRepository;
    private final PricingComponentMapper pricingComponentMapper;
    private final PricingTierMapper pricingTierMapper;
    private final PriceValueMapper priceValueMapper;
    private final TierConditionMapper tierConditionMapper;
    private final ProductPricingLinkRepository productPricingLinkRepository;
    private final KieContainerReloadService reloadService;

    // --- READ OPERATIONS ---

    @Transactional(readOnly = true)
    public List<PricingComponentResponse> findAllComponents() {
        return pricingComponentMapper.toResponseDtoList(pricingComponentRepository.findAllWithDetailsBy());
    }

    @Transactional(readOnly = true)
    public PricingComponentResponse getComponentById(Long id) {
        return pricingComponentMapper.toResponseDto(getPricingComponentById(id));
    }

    public PricingComponent getPricingComponentById(Long id) {
        return getByIdSecurely(pricingComponentRepository, id, "Pricing Component");
    }

    // --- WRITE OPERATIONS ---

    @Transactional
    @CacheEvict(value = {"publicCatalog", "productDetails"}, allEntries = true)
    public PricingComponentResponse createComponent(PricingComponentRequest requestDto) {
        validateNewVersionable(pricingComponentRepository, requestDto.getName(), requestDto.getCode());
        validateComponentType(requestDto.getType());
        PricingComponent component = pricingComponentMapper.toEntity(requestDto);
        component.setBankId(getCurrentBankId());
        component.setStatus(VersionableEntity.EntityStatus.DRAFT);
        component.setVersion(1);

        attachTiersToComponent(component, requestDto.getPricingTiers());
        validateComponentAndValueType(component);

        PricingComponent saved = pricingComponentRepository.save(component);
        reloadService.reloadKieContainer();
        return pricingComponentMapper.toResponseDto(saved);
    }

    /**
     * Versions a Pricing Component (Deep Clone).
     * Evicts cache because the catalog needs to recognize the new version availability.
     */
    @Transactional
    @CacheEvict(value = {"publicCatalog", "productDetails"}, allEntries = true)
    public Long versionComponent(Long oldId, VersionRequest request) {
        PricingComponent source = getPricingComponentById(oldId);
        PricingComponent newVersion = pricingComponentMapper.clone(source);
        prepareNewVersion(newVersion, source, request, pricingComponentRepository);
        cloneTiersInternal(source, newVersion);
        PricingComponent saved = pricingComponentRepository.save(newVersion);
        reloadService.reloadKieContainer();
        return saved.getId();
    }

    @Transactional
    @CacheEvict(value = {"publicCatalog", "productDetails"}, allEntries = true)
    public PricingComponentResponse updateComponent(Long id, PricingComponentRequest requestDto) {
        PricingComponent component = getPricingComponentById(id);
        validateDraft(component);
        if (requestDto.getType() != null) {
            validateComponentType(requestDto.getType());
        }

        pricingComponentMapper.updateFromDto(requestDto, component);
        validateComponentAndValueType(component);
        PricingComponent updated = pricingComponentRepository.save(component);
        reloadService.reloadKieContainer();
        return pricingComponentMapper.toResponseDto(updated);
    }

    // --- LIFECYCLE OPERATIONS ---

    @Transactional
    @CacheEvict(value = {"publicCatalog", "productDetails"}, allEntries = true)
    public void activateComponent(Long id) {
        PricingComponent component = getPricingComponentById(id);
        validateDraft(component);
        component.setStatus(VersionableEntity.EntityStatus.ACTIVE);
        pricingComponentRepository.save(component);
        reloadService.reloadKieContainer();
    }

    @Transactional
    @CacheEvict(value = {"publicCatalog", "productDetails"}, allEntries = true)
    public void deletePricingComponent(Long id) {
        PricingComponent component = getPricingComponentById(id);

        long linkCount = productPricingLinkRepository.countByPricingComponentId(id);
        if (linkCount > 0) {
            throw new DependencyViolationException(String.format("Cannot delete component %d: linked to %d products.", id, linkCount));
        }

        long tierCount = tierRepository.countByPricingComponentId(id);
        if (tierCount > 0) {
            throw new DependencyViolationException(String.format("Cannot delete component %d: association with %d tiers exists.", id, tierCount));
        }

        pricingComponentRepository.delete(component);
        reloadService.reloadKieContainer();
    }

    // --- INTERNAL CLONING & MAPPING LOGIC ---

    private void attachTiersToComponent(PricingComponent component, List<PricingTierRequest> tierDtos) {
        if (tierDtos == null) return;

        List<PricingTier> tiers = tierDtos.stream().map(tierDto -> {
            PricingTier tier = pricingTierMapper.toEntity(tierDto);
            tier.setPricingComponent(component);
            tier.setBankId(component.getBankId());

            if (tierDto.getConditions() != null) {
                Set<TierCondition> conditions = tierDto.getConditions().stream().map(cDto -> {
                    TierCondition condition = tierConditionMapper.toEntity(cDto);
                    condition.setPricingTier(tier);
                    condition.setBankId(component.getBankId());
                    return condition;
                }).collect(Collectors.toSet());
                tier.setConditions(conditions);
            }

            PriceValue value = priceValueMapper.toEntity(tierDto.getPriceValue());
            value.setPricingTier(tier);
            value.setBankId(component.getBankId());
            tier.setPriceValues(Set.of(value));

            return tier;
        }).toList();

        component.setPricingTiers(tiers);
    }

    private void cloneTiersInternal(PricingComponent source, PricingComponent target) {
        if (source.getPricingTiers() == null) return;

        List<PricingTier> clonedTiers = source.getPricingTiers().stream().map(oldTier -> {
            PricingTier newTier = pricingTierMapper.clone(oldTier);
            newTier.setPricingComponent(target);
            newTier.setBankId(target.getBankId());

            if (oldTier.getPriceValues() != null) {
                Set<PriceValue> newValues = oldTier.getPriceValues().stream().map(oldVal -> {
                    PriceValue newVal = priceValueMapper.clone(oldVal);
                    newVal.setPricingTier(newTier);
                    newVal.setBankId(target.getBankId());
                    return newVal;
                }).collect(Collectors.toSet());
                newTier.setPriceValues(newValues);
            }

            if (oldTier.getConditions() != null) {
                Set<TierCondition> newConditions = oldTier.getConditions().stream().map(oldCond -> {
                    TierCondition newCond = tierConditionMapper.toEntity(tierConditionMapper.toDto(oldCond));
                    newCond.setPricingTier(newTier);
                    newCond.setBankId(target.getBankId());
                    return newCond;
                }).collect(Collectors.toSet());
                newTier.setConditions(newConditions);
            }

            return newTier;
        }).toList();

        target.setPricingTiers(clonedTiers);
    }

    // --- GRANULAR TIER OPERATIONS (Required by PricingTierController) ---

    @Transactional
    public ProductPricingCalculationResult.PriceComponentDetail addTierAndValue(
            Long componentId,
            PricingTierRequest tierDto,
            PriceValueRequest valueDto) {

        PricingComponent component = getPricingComponentById(componentId);
        validateDraft(component);
        validatePriceValueType(valueDto.getValueType());

        PricingTier tier = pricingTierMapper.toEntity(tierDto);
        tier.setPricingComponent(component);
        tier.setBankId(component.getBankId());

        PriceValue value = priceValueMapper.toEntity(valueDto);
        value.setPricingTier(tier);
        value.setBankId(component.getBankId());
        tier.setPriceValues(Set.of(value));

        mapConditions(tierDto, tier);
        validateComponentAndValueType(component);

        PricingTier saved = tierRepository.save(tier);
        reloadService.reloadKieContainer();

        return priceValueMapper.toDetailDto(saved.getPriceValues().iterator().next());
    }

    @Transactional
    public ProductPricingCalculationResult.PriceComponentDetail updateTierAndValue(
            Long componentId,
            Long tierId,
            PricingTierRequest tierDto) {

        // 1. Fetch and validate Component first (Ensures tenancy and state)
        PricingComponent component = getPricingComponentById(componentId);
        validateDraft(component);

        // 2. Fetch Tier and verify ownership
        PricingTier tier = tierRepository.findById(tierId)
                .orElseThrow(() -> new NotFoundException("Tier not found with ID: " + tierId));

        if (!tier.getPricingComponent().getId().equals(componentId)) {
            throw new NotFoundException("Tier " + tierId + " does not belong to component " + componentId);
        }

        // 3. Validate ValueType if present
        if (tierDto.getPriceValue() != null) {
            validatePriceValueType(tierDto.getPriceValue().getValueType());
        }

        // 4. Update core fields
        pricingTierMapper.updateFromDto(tierDto, tier);

        // 5. Handle Conditions explicitly (Clear if empty list provided)
        if (tierDto.getConditions() != null) {
            tier.getConditions().clear();
            if (!tierDto.getConditions().isEmpty()) {
                mapConditions(tierDto, tier);
            }
        }

        // 6. Update PriceValue
        if (tierDto.getPriceValue() != null && !tier.getPriceValues().isEmpty()) {
            priceValueMapper.updateFromDto(tierDto.getPriceValue(), tier.getPriceValues().iterator().next());
        }

        validateComponentAndValueType(component);
        PricingTier updated = tierRepository.save(tier);
        reloadService.reloadKieContainer();

        return priceValueMapper.toDetailDto(updated.getPriceValues().iterator().next());
    }

    @Transactional
    public void deleteTierAndValue(Long componentId, Long tierId) {
        PricingComponent component = getPricingComponentById(componentId);
        validateDraft(component);

        PricingTier tier = tierRepository.findById(tierId)
                .filter(t -> t.getPricingComponent().getId().equals(componentId))
                .orElseThrow(() -> new NotFoundException("Tier " + tierId + " not found for component " + componentId));

        tierRepository.delete(tier);
        reloadService.reloadKieContainer();
    }

    // --- HELPER VALIDATIONS ---

    private void mapConditions(PricingTierRequest tierDto, PricingTier tier) {
        if (tierDto.getConditions() == null) return;

        Set<TierCondition> conditions = tierDto.getConditions().stream()
                .map(cDto -> {
                    TierCondition c = tierConditionMapper.toEntity(cDto);
                    c.setPricingTier(tier);
                    c.setBankId(tier.getBankId());
                    return c;
                }).collect(Collectors.toSet());

        if (tier.getConditions() == null) {
            tier.setConditions(conditions);
        } else {
            tier.getConditions().addAll(conditions);
        }
    }

    private void validatePriceValueType(String type) {
        try {
            PriceValue.ValueType.valueOf(type.toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid value type provided: " + type);
        }
    }

    private void validateComponentType(String type) {
        try {
            PricingComponent.ComponentType.valueOf(type.toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid component type provided: " + type);
        }
    }

    private void validateComponentAndValueType(PricingComponent component) {
        if (component.getPricingTiers() == null) return;

        for (PricingTier tier : component.getPricingTiers()) {
            if (tier.getPriceValues() == null) continue;
            for (PriceValue value : tier.getPriceValues()) {
                if (isDiscountType(component.getType())) {
                    if (!isDiscountValue(value.getValueType())) {
                        throw new IllegalArgumentException(String.format(
                                "Component type %s must have discount-related value types (DISCOUNT_PERCENTAGE, DISCOUNT_ABSOLUTE, FREE_COUNT). Found: %s",
                                component.getType(), value.getValueType()));
                    }
                } else if (isFeeType(component.getType())) {
                    if (!isFeeValue(value.getValueType())) {
                        throw new IllegalArgumentException(String.format(
                                "Component type %s must have fee-related value types (FEE_ABSOLUTE, FEE_PERCENTAGE). Found: %s",
                                component.getType(), value.getValueType()));
                    }
                }
            }
        }
    }

    private boolean isDiscountType(PricingComponent.ComponentType type) {
        return type == PricingComponent.ComponentType.WAIVER ||
                type == PricingComponent.ComponentType.BENEFIT ||
                type == PricingComponent.ComponentType.DISCOUNT;
    }

    private boolean isFeeType(PricingComponent.ComponentType type) {
        return type == PricingComponent.ComponentType.FEE ||
                type == PricingComponent.ComponentType.INTEREST_RATE ||
                type == PricingComponent.ComponentType.PACKAGE_FEE ||
                type == PricingComponent.ComponentType.TAX;
    }

    private boolean isDiscountValue(PriceValue.ValueType type) {
        return type == PriceValue.ValueType.DISCOUNT_PERCENTAGE ||
                type == PriceValue.ValueType.DISCOUNT_ABSOLUTE ||
                type == PriceValue.ValueType.FREE_COUNT;
    }

    private boolean isFeeValue(PriceValue.ValueType type) {
        return type == PriceValue.ValueType.FEE_ABSOLUTE ||
                type == PriceValue.ValueType.FEE_PERCENTAGE;
    }
}