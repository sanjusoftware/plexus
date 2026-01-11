package com.bankengine.pricing;

import com.bankengine.auth.model.Role;
import com.bankengine.auth.repository.RoleRepository;
import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductBundleRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.pricing.model.*;
import com.bankengine.pricing.repository.*;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class TestTransactionHelper {

    @Autowired private PricingComponentRepository componentRepository;
    @Autowired private PricingTierRepository tierRepository;
    @Autowired private PriceValueRepository valueRepository;
    @Autowired private PricingInputMetadataRepository metadataRepository;
    @Autowired private TierConditionRepository tierConditionRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private RoleRepository roleRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductTypeRepository productTypeRepository;
    @Autowired private ProductBundleRepository productBundleRepository;
    @Autowired private ProductPricingLinkRepository productPricingLinkRepository;


    // =================================================================
    // Find-or-Create DSL for Catalog Entities (DRY)
    // =================================================================

    @Transactional
    public void cleanupCommittedMetadata() {
        metadataRepository.findByAttributeKey("customerSegment")
                .ifPresent(metadataRepository::delete);
        metadataRepository.findByAttributeKey("transactionAmount")
                .ifPresent(metadataRepository::delete);
        flushAndClear();
    }

    @Transactional
    public PricingComponent createPricingComponentInDb(String name) {
        PricingComponent component = new PricingComponent();
        component.setName(name);
        component.setType(PricingComponent.ComponentType.RATE);
        return componentRepository.save(component);
    }

    @Transactional
    public void linkProductToPricingComponent(Long productId, Long componentId, BigDecimal fixedValue) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalStateException("Product not found"));
        PricingComponent component = componentRepository.findById(componentId)
                .orElseThrow(() -> new IllegalStateException("Pricing Component not found"));

        ProductPricingLink link = new ProductPricingLink();
        link.setProduct(product);
        link.setPricingComponent(component);
        link.setFixedValue(fixedValue);
        link.setUseRulesEngine(false);
        link.setBankId(product.getBankId());

        productPricingLinkRepository.save(link);
    }

    @Transactional
    public PricingTier createCommittedTierDependency(Long componentId, String tierName) {
        PricingComponent component = componentRepository.findById(componentId)
                .orElseThrow(() -> new RuntimeException("Component not found"));

        PricingTier tier = new PricingTier();
        tier.setPricingComponent(component);
        tier.setTierName(tierName);
        tier.setMinThreshold(BigDecimal.ZERO);

        TierCondition condition = new TierCondition();
        condition.setPricingTier(tier);
        condition.setAttributeName("customerSegment");
        condition.setOperator(TierCondition.Operator.EQ);
        condition.setAttributeValue("DEFAULT_SEGMENT");
        tier.setConditions(new HashSet<>(Set.of(condition)));

        return tierRepository.save(tier);
    }

    @Transactional
    public Long createLinkedTierAndValue(String componentName, String tierName) {
        PricingComponent component = createPricingComponentInDb(componentName);
        PricingTier tier = createCommittedTierDependency(component.getId(), tierName);

        PriceValue value = new PriceValue();
        value.setPricingTier(tier);
        value.setPriceAmount(new BigDecimal("10.00"));
        value.setValueType(PriceValue.ValueType.ABSOLUTE);
        valueRepository.save(value);

        return component.getId();
    }

    /**
     * Idempotently retrieves or creates a ProductType by name within the current bank context.
     */
    @Transactional
    public ProductType getOrCreateProductType(String name) {
        return productTypeRepository.findByName(name)
                .orElseGet(() -> {
                    ProductType pt = new ProductType();
                    pt.setName(name);
                    // bank_id is handled by AuditableEntity filter/auditor
                    return productTypeRepository.save(pt);
                });
    }

    /**
     * Idempotently retrieves or creates a Product.
     */
    @Transactional
    public Product getOrCreateProduct(String name, ProductType type, String category) {
        return productRepository.findByName(name)
                .orElseGet(() -> {
                    Product p = new Product();
                    p.setName(name);
                    p.setProductType(type);
                    p.setCategory(category);
                    p.setStatus("ACTIVE");
                    p.setEffectiveDate(LocalDate.now().minusDays(1));
                    return productRepository.save(p);
                });
    }

    // =================================================================
    // Authentication/Role Helpers
    // =================================================================

    @Transactional
    public Role createRoleInDb(String roleName, Set<String> authorities) {
        return roleRepository.findByName(roleName)
                .map(existingRole -> {
                    Set<String> currentAuthorities = existingRole.getAuthorities();
                    Set<String> combinedAuthorities = new HashSet<>(currentAuthorities);
                    combinedAuthorities.addAll(authorities);

                    if (combinedAuthorities.size() > currentAuthorities.size()) {
                        existingRole.setAuthorities(combinedAuthorities);
                        return roleRepository.save(existingRole);
                    }
                    return existingRole;
                })
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setName(roleName);
                    newRole.setAuthorities(authorities);
                    return roleRepository.save(newRole);
                });
    }

    // =================================================================
    // Pricing Metadata & Graph Helpers
    // =================================================================

    @Transactional
    public void setupCommittedMetadata() {
        createAndSaveMetadata("customerSegment", "STRING");
        createAndSaveMetadata("transactionAmount", "DECIMAL");
        flushAndClear();
    }

    @Transactional
    public PricingInputMetadata createAndSaveMetadata(String key, String dataType) {
        return metadataRepository.findByAttributeKey(key).orElseGet(() -> {
            PricingInputMetadata metadata = new PricingInputMetadata();
            metadata.setAttributeKey(key);
            metadata.setDataType(dataType);
            metadata.setDisplayName(key);
            return metadataRepository.save(metadata);
        });
    }

    @Transactional
    public void deleteComponentGraphById(Long componentId) {
        componentRepository.findById(componentId).ifPresent(component -> {
            List<Long> tierIds = component.getPricingTiers().stream().map(PricingTier::getId).toList();
            tierConditionRepository.deleteByPricingTierIdIn(tierIds);
            valueRepository.deleteByPricingTierIdIn(tierIds);
            tierRepository.deleteAllById(tierIds);
            componentRepository.delete(component);
            entityManager.flush();
        });
    }

    // =================================================================
    // Transactional Utilities
    // =================================================================

    @Transactional
    public <T> T doInTransaction(java.util.function.Supplier<T> action) {
        return action.get();
    }

    @Transactional
    public void doInTransaction(Runnable action) {
        action.run();
    }

    @Transactional
    public void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * Legacy helper kept for backward compatibility with older tests,
     * but updated to use internal find-or-create logic.
     */
    @Transactional
    public Long createProductInDb(String name, Long productTypeId, String category) {
        ProductType type = productTypeRepository.findById(productTypeId)
                .orElseThrow(() -> new IllegalStateException("Type not found"));
        return getOrCreateProduct(name, type, category).getId();
    }

    @Transactional
    public ProductBundle createBundleInDb(String name) {
        ProductBundle bundle = new ProductBundle();
        bundle.setName(name);
        // Code must be unique per bank_id; using a timestamp or UUID prevents collisions
        bundle.setCode("BNDL_" + System.currentTimeMillis());
        bundle.setEligibilitySegment("RETAIL");
        bundle.setActivationDate(LocalDate.now());
        bundle.setStatus(ProductBundle.BundleStatus.ACTIVE);
        bundle.setBankId(TenantContextHolder.getBankId());

        return productBundleRepository.save(bundle);
    }
}