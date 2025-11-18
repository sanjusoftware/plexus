package com.bankengine.pricing;

import com.bankengine.pricing.model.*;
import com.bankengine.pricing.repository.*;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class TestTransactionHelper {

    @Autowired
    private PricingComponentRepository componentRepository;
    @Autowired
    private PricingTierRepository tierRepository;
    @Autowired
    private PriceValueRepository valueRepository;
    @Autowired
    private PricingInputMetadataRepository metadataRepository;
    @Autowired
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setupCommittedMetadata() {
        createAndSaveMetadata("customerSegment", "STRING");
        createAndSaveMetadata("transactionAmount", "DECIMAL");
        entityManager.flush();
        entityManager.clear();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PricingInputMetadata createAndSaveMetadata(String key, String dataType) {
        return metadataRepository.findByAttributeKey(key).orElseGet(() -> {
            PricingInputMetadata metadata = new PricingInputMetadata();
            metadata.setAttributeKey(key);
            metadata.setDataType(dataType);
            metadata.setDisplayName(key);
            return metadataRepository.save(metadata);
        });
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

        // Add a Condition
        TierCondition condition = new TierCondition();
        condition.setPricingTier(tier);
        condition.setAttributeName("customerSegment");
        condition.setOperator(TierCondition.Operator.EQ);
        condition.setAttributeValue("DEFAULT_SEGMENT");

        tier.setConditions(new HashSet<>(Set.of(condition)));

        PricingTier savedTier = tierRepository.save(tier);

        // 3. Create Value
        PriceValue value = new PriceValue();
        value.setPricingTier(savedTier);
        value.setPriceAmount(new BigDecimal("10.00"));
        value.setCurrency("USD");
        value.setValueType(PriceValue.ValueType.ABSOLUTE);
        valueRepository.save(value);

        savedComponent.setPricingTiers(new ArrayList<>(List.of(savedTier)));
        componentRepository.save(savedComponent);

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
        tier.setMinThreshold(new BigDecimal("0.00"));

        // Add a condition
        TierCondition condition = new TierCondition();
        condition.setPricingTier(tier);
        condition.setAttributeName("customerSegment");
        condition.setOperator(TierCondition.Operator.EQ);
        condition.setAttributeValue("DEFAULT_SEGMENT");

        tier.setConditions(new HashSet<>(Set.of(condition)));

        return tierRepository.save(tier);
    }

    @Autowired
    private TierConditionRepository tierConditionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createTierCondition(String attributeName) {
        PricingComponent component = createPricingComponentInDb("test-component");
        PricingTier tier = new PricingTier();
        tier.setPricingComponent(component);
        tier.setTierName("test-tier");
        tier.setMinThreshold(BigDecimal.ZERO);
        PricingTier savedTier = tierRepository.save(tier);

        TierCondition condition = new TierCondition();
        condition.setPricingTier(savedTier);
        condition.setAttributeName(attributeName);
        condition.setOperator(TierCondition.Operator.EQ);
        condition.setAttributeValue("someValue");
        tierConditionRepository.save(condition);
    }
}