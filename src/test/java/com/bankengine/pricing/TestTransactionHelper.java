package com.bankengine.pricing;

import com.bankengine.auth.model.Role;
import com.bankengine.auth.repository.RoleRepository;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.pricing.model.*;
import com.bankengine.pricing.repository.*;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private TierConditionRepository tierConditionRepository; // Now used directly
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductTypeRepository productTypeRepository;

    // =================================================================
    // Pricing Metadata Helpers
    // =================================================================

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

    // =================================================================
    // Pricing Component/Tier Helpers
    // =================================================================

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

    // =================================================================
    // Authentication/Role Helpers
    // =================================================================

    /**
     * Creates and saves a Role entity with the specified authorities in a new transaction.
     * Uses the find-or-create pattern to ensure idempotence and avoid unique constraint violations
     * when run across multiple test classes.
     *
     * @param roleName The name of the role (e.g., "ADMIN").
     * @param authorities The set of permissions assigned to this role.
     * @return The persisted Role entity.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Role createRoleInDb(String roleName, Set<String> authorities) {

        // 1. Find or Create the Role
        return roleRepository.findByName(roleName)
                .map(existingRole -> {
                    // Role FOUND (This is the scenario causing problems across the suite)

                    // 1. Get the current authorities
                    Set<String> currentAuthorities = existingRole.getAuthorities();

                    // 2. Combine the current authorities with the new ones needed for this test class
                    Set<String> combinedAuthorities = new java.util.HashSet<>(currentAuthorities);
                    combinedAuthorities.addAll(authorities); // Ensures the union

                    // 3. Update and save the role only if the authority set has actually changed
                    if (combinedAuthorities.size() > currentAuthorities.size()) {
                        existingRole.setAuthorities(combinedAuthorities);
                        return roleRepository.save(existingRole);
                    }
                    return existingRole;
                })
                .orElseGet(() -> {
                    // Role NOT FOUND (Initial creation)
                    Role newRole = new Role();
                    newRole.setName(roleName);
                    newRole.setAuthorities(authorities);
                    // ... set other fields (bankId, etc.)
                    return roleRepository.save(newRole);
                });
    }

    /**
     * Creates a Product entity directly in the database, bypassing API security,
     * to facilitate setup for integration tests.
     */
    public Long createProductInDb(String name, Long productTypeId) {
        Product product = new Product();
        product.setName(name);
        product.setBankId("DB-SETUP");
        product.setStatus("DRAFT");
        product.setEffectiveDate(LocalDate.now().plusDays(1));

        // Fetch the ProductType within the transaction boundary
        ProductType productType = productTypeRepository.findById(productTypeId)
                .orElseThrow(() -> new IllegalStateException("ProductType ID " + productTypeId + " not found during setup."));

        product.setProductType(productType);
        return productRepository.save(product).getId();
    }

    /**
     * Executes a lambda within a new transaction context.
     * Useful for verifying data committed by other transactional methods.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T doInTransaction(java.util.function.Supplier<T> action) {
        return action.get();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void doInTransaction(Runnable action) {
        action.run();
    }

    /**
     * Forces all pending changes to the database and clears the Hibernate/JPA session cache.
     * This is crucial for @BeforeAll setups to ensure subsequent security context loading
     * (e.g., @WithMockRole) sees the freshly committed data.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}