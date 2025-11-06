package com.bankengine.pricing;

import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.repository.PriceValueRepository;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.repository.PricingTierRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
public class TestTransactionHelper {

    @Autowired
    private PricingComponentRepository componentRepository;
    @Autowired
    private PricingTierRepository tierRepository;
    @Autowired
    private PriceValueRepository valueRepository;
    @Autowired
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW) // <-- CRITICAL
    public PricingComponent createPricingComponentInDb(String name) {
        PricingComponent component = new PricingComponent();
        component.setName(name);
        component.setType(PricingComponent.ComponentType.RATE);

        return componentRepository.save(component);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createLinkedTierAndValue(String componentName, String tierName) {
        // 1. Create Component
        PricingComponent component = new PricingComponent();
        component.setName(componentName);
        component.setType(PricingComponent.ComponentType.RATE);
        PricingComponent savedComponent = componentRepository.save(component);

        // 2. Create Tier
        PricingTier tier = new PricingTier();
        tier.setPricingComponent(savedComponent);
        tier.setTierName(tierName);
        tier.setMinThreshold(new BigDecimal("0.00"));
        PricingTier savedTier = tierRepository.save(tier);

        // 3. Create Value
        PriceValue value = new PriceValue();
        value.setPricingTier(savedTier);
        value.setPriceAmount(new BigDecimal("10.00"));
        value.setCurrency("USD");
        value.setValueType(PriceValue.ValueType.ABSOLUTE);
        valueRepository.save(value);

        return savedComponent.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PricingTier createCommittedTierDependency(Long componentId, String tierName) {
        // Fetch the committed component
        PricingComponent component = componentRepository.findById(componentId)
                .orElseThrow(() -> new RuntimeException("Component not found for tier setup!"));

        PricingTier tier = new PricingTier();
        tier.setPricingComponent(component);
        tier.setTierName(tierName);
        tier.setMinThreshold(new BigDecimal("0.00")); // Ensure mandatory fields are set

        // Save and commit the tier in this new transaction
        return tierRepository.save(tier);
    }
}