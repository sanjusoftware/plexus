package com.bankengine.pricing;

import com.bankengine.auth.model.Role;
import com.bankengine.auth.repository.RoleRepository;
import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.model.BundleProductLink;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.BundleProductLinkRepository;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static com.bankengine.test.config.AbstractIntegrationTest.TEST_BANK_ID;

@Component
public class TestTransactionHelper {

    @Autowired
    private PricingComponentRepository pricingComponentRepository;
    @Autowired
    private PricingTierRepository tierRepository;
    @Autowired
    private PriceValueRepository valueRepository;
    @Autowired
    private PricingInputMetadataRepository metadataRepository;
    @Autowired
    private TierConditionRepository tierConditionRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductTypeRepository productTypeRepository;
    @Autowired
    private ProductBundleRepository productBundleRepository;
    @Autowired
    private ProductPricingLinkRepository productPricingLinkRepository;
    @Autowired
    private BundlePricingLinkRepository bundlePricingLinkRepository;
    @Autowired
    private BundleProductLinkRepository bundleProductLinkRepository;

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
        return pricingComponentRepository.findByName(name)
                .orElseGet(() -> {
                    PricingComponent component = new PricingComponent();
                    component.setName(name);
                    component.setBankId(TenantContextHolder.getBankId());
                    component.setType(PricingComponent.ComponentType.FEE); // or set a default
                    return pricingComponentRepository.save(component);
                });
    }

    @Transactional
    public void linkProductToPricingComponent(Long productId, Long componentId, BigDecimal fixedValue, PriceValue.ValueType type) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalStateException("Product not found"));
        PricingComponent component = pricingComponentRepository.findById(componentId)
                .orElseThrow(() -> new IllegalStateException("Pricing Component not found"));

        // Ensure at least one tier exists so the Service's INNER JOIN doesn't fail
        if (component.getPricingTiers() == null || component.getPricingTiers().isEmpty()) {
            createCommittedTierDependency(componentId, "Default Base Tier");
        }

        ProductPricingLink link = new ProductPricingLink();
        link.setProduct(product);
        link.setPricingComponent(component);
        link.setFixedValue(fixedValue);
        link.setFixedValueType(type);
        link.setUseRulesEngine(false);
        link.setBankId(product.getBankId());
        link.setEffectiveDate(LocalDate.now().minusDays(1));
        link.setExpiryDate(LocalDate.now().plusYears(10));

        productPricingLinkRepository.save(link);
    }

    @Transactional
    public void linkProductToPricingComponent(Long productId, Long componentId, BigDecimal fixedValue) {
        linkProductToPricingComponent(productId, componentId, fixedValue, PriceValue.ValueType.FEE_ABSOLUTE);
    }

    @Transactional
    public void linkBundleToPricingComponent(Long bundleId, Long componentId, BigDecimal fixedValue) {
        // For bundles, if fixedValue is negative, it's usually a DISCOUNT_ABSOLUTE
        PriceValue.ValueType type = (fixedValue.compareTo(BigDecimal.ZERO) < 0)
                ? PriceValue.ValueType.DISCOUNT_ABSOLUTE
                : PriceValue.ValueType.FEE_ABSOLUTE;

        linkBundleToPricingComponent(bundleId, componentId, fixedValue, type);
    }

    @Transactional
    public void linkBundleToPricingComponent(Long bundleId, Long componentId, BigDecimal fixedValue, PriceValue.ValueType type) {
        ProductBundle bundle = productBundleRepository.findById(bundleId)
                .orElseThrow(() -> new IllegalStateException("Bundle not found"));
        PricingComponent component = pricingComponentRepository.findById(componentId)
                .orElseThrow(() -> new IllegalStateException("Pricing Component not found"));

        if (component.getPricingTiers() == null || component.getPricingTiers().isEmpty()) {
            createCommittedTierDependency(componentId, "Bundle Benefit Tier");
        }

        BundlePricingLink link = new BundlePricingLink();
        link.setProductBundle(bundle);
        link.setPricingComponent(component);
        link.setFixedValue(fixedValue);
        link.setFixedValueType(type);
        link.setUseRulesEngine(false);
        link.setBankId(bundle.getBankId());
        link.setEffectiveDate(LocalDate.now().minusDays(1));
        link.setExpiryDate(LocalDate.now().plusYears(10));

        if (bundle.getBundlePricingLinks() == null) {
            bundle.setBundlePricingLinks(new ArrayList<>());
        }
        bundle.getBundlePricingLinks().add(link);
        bundlePricingLinkRepository.save(link);
    }

    @Transactional
    public PricingTier createCommittedTierDependency(Long componentId, String tierName) {
        PricingComponent component = pricingComponentRepository.findById(componentId)
                .orElseThrow(() -> new RuntimeException("Component not found"));

        PricingTier tier = new PricingTier();
        tier.setPricingComponent(component);
        tier.setTierName(tierName);
        tier.setMinThreshold(BigDecimal.ZERO);
        tier.setEffectiveDate(LocalDate.now().minusDays(1));
        tier.setExpiryDate(LocalDate.now().plusYears(10));
        tier.setProRataApplicable(false);
        tier.setApplyChargeOnFullBreach(false);

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
        value.setRawValue(new BigDecimal("10.00"));
        value.setValueType(PriceValue.ValueType.FEE_ABSOLUTE);
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
                    p.setBankId(TenantContextHolder.getBankId());
                    p.setEffectiveDate(LocalDate.now().minusDays(1));
                    return productRepository.save(p);
                });
    }

    @Transactional
    public Role getOrCreateRoleInDb(String roleName, Set<String> authorities) {
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
        pricingComponentRepository.findById(componentId).ifPresent(component -> {
            List<Long> tierIds = component.getPricingTiers().stream().map(PricingTier::getId).toList();
            tierConditionRepository.deleteByPricingTierIdIn(tierIds);
            valueRepository.deleteByPricingTierIdIn(tierIds);
            tierRepository.deleteAllById(tierIds);
            pricingComponentRepository.delete(component);
            entityManager.flush();
        });
    }

    @Transactional
    public <T> T doInTransaction(Supplier<T> action) {
        return action.get();
    }

    @Transactional
    public void doInTransaction(Runnable action) {
        TenantContextHolder.setBankId(TEST_BANK_ID);
        action.run();
    }

    @Transactional
    public void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Transactional
    public Long createProductInDb(String name, Long productTypeId, String category) {
        ProductType type = productTypeRepository.findById(productTypeId)
                .orElseThrow(() -> new IllegalStateException("Type not found"));
        return getOrCreateProduct(name, type, category).getId();
    }

    @Transactional
    public void linkProductToBundle(Long bundleId, Long productId, boolean isMain) {
        ProductBundle bundle = productBundleRepository.findById(bundleId)
                .orElseThrow(() -> new IllegalStateException("Bundle not found"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalStateException("Product not found"));

        BundleProductLink link = new BundleProductLink(bundle, product, true, isMain);
        link.setBankId(bundle.getBankId());
        bundleProductLinkRepository.save(link);

        // Update the in-memory collection so bundle.getContainedProducts() isn't empty
        if (bundle.getContainedProducts() == null) {
            bundle.setContainedProducts(new ArrayList<>());
        }
        bundle.getContainedProducts().add(link);
    }

    @Transactional
    public ProductPricingLink linkProductToPricingComponentReturn(Long productId, Long componentId, BigDecimal fixedValue, PriceValue.ValueType type) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalStateException("Product not found"));
        PricingComponent component = pricingComponentRepository.findById(componentId)
                .orElseThrow(() -> new IllegalStateException("Pricing Component not found"));

        ProductPricingLink link = new ProductPricingLink();
        link.setProduct(product);
        link.setPricingComponent(component);
        link.setFixedValue(fixedValue);
        link.setFixedValueType(type);
        link.setUseRulesEngine(false);
        link.setBankId(product.getBankId());
        link.setEffectiveDate(LocalDate.now().minusDays(1)); // Ensures visibility today
        link.setExpiryDate(LocalDate.now().plusYears(10));

        return productPricingLinkRepository.save(link);
    }

    @Transactional
    public ProductBundle createBundleInDb(String name, ProductBundle.BundleStatus bundleStatus) {
        ProductBundle bundle = new ProductBundle();
        bundle.setName(name);
        bundle.setCode("BNDL_" + System.currentTimeMillis());
        bundle.setEligibilitySegment("RETAIL");
        bundle.setActivationDate(LocalDate.now());
        bundle.setStatus(bundleStatus);
        bundle.setBankId(TenantContextHolder.getBankId());

        return productBundleRepository.save(bundle);
    }

    /**
     * Creates a complete Bundle graph: Bundle -> Linked Product -> Bundle Pricing Adjustment
     */
    @Transactional
    public ProductBundle setupFullBundleWithPricing(String bundleName, String productName, BigDecimal discountValue, PriceValue.ValueType discountType, ProductBundle.BundleStatus bundleStatus) {
        ProductType defaultType = getOrCreateProductType("SAVINGS");

        // 1. Ensure product exists
        Product product = getOrCreateProduct(productName, defaultType, "RETAIL");

        // 2. Ensure the product HAS an active pricing link.
        // The PricingCalculationService throws 404 if a product in a bundle has no price.
        PricingComponent productBaseFee = createPricingComponentInDb("Standard Base Fee");

        // We use a specific method to ensure the link is saved and flushed
        linkProductToPricingComponent(product.getId(), productBaseFee.getId(), BigDecimal.ZERO);

        // 3. Create bundle
        ProductBundle bundle = createBundleInDb(bundleName, bundleStatus);

        // 4. Link product to bundle
        linkProductToBundle(bundle.getId(), product.getId(), true);

        // 5. Link the benefit (discount)
        linkBundleToPricingComponent(bundle.getId(), createPricingComponentInDb(bundleName + " Benefit").getId(), discountValue, discountType);

        // 6. Flush to disk and clear cache so the Service sees clean DB state
        entityManager.flush();
        entityManager.clear();

        return productBundleRepository.findById(bundle.getId())
                .orElseThrow(() -> new IllegalStateException("Bundle lost after clear"));
    }
}