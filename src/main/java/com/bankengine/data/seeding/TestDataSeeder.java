package com.bankengine.data.seeding;

import com.bankengine.auth.model.Role;
import com.bankengine.auth.repository.RoleRepository;
import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.auth.service.AuthorityDiscoveryService;
import com.bankengine.catalog.model.*;
import com.bankengine.catalog.model.FeatureComponent.DataType;
import com.bankengine.catalog.repository.*;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.CategoryConflictRule;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.pricing.model.*;
import com.bankengine.pricing.model.PricingComponent.ComponentType;
import com.bankengine.pricing.model.TierCondition.Operator;
import com.bankengine.pricing.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Profile("dev")
public class TestDataSeeder implements CommandLineRunner {

    private static final String BANK_A = "GLOBAL-BANK-001";
    private static final String BANK_B = "LOCAL-BANK-002";

    private final ProductTypeRepository productTypeRepository;
    private final FeatureComponentRepository featureComponentRepository;
    private final ProductRepository productRepository;
    private final ProductFeatureLinkRepository linkRepository;
    private final PricingComponentRepository pricingComponentRepository;
    private final PricingTierRepository pricingTierRepository;
    private final PriceValueRepository priceValueRepository;
    private final ProductPricingLinkRepository productPricingLinkRepository;
    private final TierConditionRepository tierConditionRepository;
    private final RoleRepository roleRepository;
    private final BankConfigurationRepository bankConfigurationRepository;
    private final ProductBundleRepository productBundleRepository;
    private final ApplicationContext applicationContext;
    private final CoreMetadataSeeder coreMetadataSeeder;
    private final AuthorityDiscoveryService authorityDiscoveryService;

    public TestDataSeeder(
            ProductTypeRepository productTypeRepository,
            FeatureComponentRepository featureComponentRepository,
            ProductRepository productRepository,
            ProductFeatureLinkRepository linkRepository,
            PricingComponentRepository pricingComponentRepository,
            PricingTierRepository pricingTierRepository,
            PriceValueRepository priceValueRepository,
            ProductPricingLinkRepository productPricingLinkRepository,
            TierConditionRepository tierConditionRepository,
            RoleRepository roleRepository,
            BankConfigurationRepository bankConfigurationRepository,
            ProductBundleRepository productBundleRepository,
            ApplicationContext applicationContext, CoreMetadataSeeder coreMetadataSeeder,
            AuthorityDiscoveryService authorityDiscoveryService) {
        this.productTypeRepository = productTypeRepository;
        this.featureComponentRepository = featureComponentRepository;
        this.productRepository = productRepository;
        this.linkRepository = linkRepository;
        this.pricingComponentRepository = pricingComponentRepository;
        this.pricingTierRepository = pricingTierRepository;
        this.priceValueRepository = priceValueRepository;
        this.productPricingLinkRepository = productPricingLinkRepository;
        this.tierConditionRepository = tierConditionRepository;
        this.roleRepository = roleRepository;
        this.bankConfigurationRepository = bankConfigurationRepository;
        this.productBundleRepository = productBundleRepository;
        this.applicationContext = applicationContext;
        this.coreMetadataSeeder = coreMetadataSeeder;
        this.authorityDiscoveryService = authorityDiscoveryService;
    }

    @Override
    public void run(String... args) {
        System.out.println("--- Seeding Comprehensive Development Data ---");
        TestDataSeeder proxy = applicationContext.getBean(TestDataSeeder.class);

        for (String bankId : List.of(BANK_A, BANK_B)) {
            TenantContextHolder.setSystemMode(true);
            System.out.println("\n--- Seeding Tenant: " + bankId + " ---");
            TenantContextHolder.setBankId(bankId);

            proxy.seedBankConfiguration(bankId);
            proxy.seedTestRoles(bankId);
            proxy.seedPricingInputMetadata(bankId);
            proxy.seedProductTypes(bankId);
            proxy.seedFeaturesAndProducts(bankId);
            proxy.seedPricingComponentsAndLinks(bankId);
            proxy.seedBundles(bankId);

            TenantContextHolder.clear();
        }
        System.out.println("\n--- Seeding Complete ---");
    }

    @Transactional
    public void seedBankConfiguration(String bankId) {
        if (bankConfigurationRepository.findByBankId(bankId).isEmpty()) {
            BankConfiguration config = new BankConfiguration();
            config.setBankId(bankId);
            config.setAllowProductInMultipleBundles(bankId.equals(BANK_A));

            List<CategoryConflictRule> rules = new ArrayList<>();
            rules.add(new CategoryConflictRule("RETAIL", "WEALTH"));
            config.setCategoryConflictRules(rules);

            bankConfigurationRepository.save(config);
            System.out.println("Seeded Bank Configuration for " + bankId);
        }
    }

    @Transactional
    public void seedTestRoles(String bankId) {
        // 1. Discover all authorities from the code using reflection
        Set<String> allSystemAuthorities = authorityDiscoveryService.discoverAllAuthorities();

        // 2. Check if the SUPER_ADMIN role exists for this bank
        roleRepository.findByName("SUPER_ADMIN").ifPresentOrElse(
                existingRole -> {
                    // For dev mode, we update the existing role to ensure it gets NEWLY created permissions
                    existingRole.setAuthorities(new HashSet<>(allSystemAuthorities));
                    roleRepository.save(existingRole);
                    System.out.println("Updated SUPER_ADMIN for " + bankId + " with " + allSystemAuthorities.size() + " permissions.");
                },
                () -> {
                    Role admin = new Role();
                    admin.setName("SUPER_ADMIN");
                    admin.setAuthorities(new HashSet<>(allSystemAuthorities));
                    admin.setBankId(bankId);
                    roleRepository.save(admin);
                    System.out.println("Seeded new SUPER_ADMIN for " + bankId + " with " + allSystemAuthorities.size() + " permissions.");
                }
        );
    }

    @Transactional
    public void seedPricingInputMetadata(String bankId) {
        coreMetadataSeeder.seedCorePricingInputMetadata(bankId);
    }

    @Transactional
    public void seedProductTypes(String bankId) {
        if (productTypeRepository.findByName("CASA").isEmpty()) {
            productTypeRepository.save(createType("CASA", bankId));
            System.out.println("Seeded Product Types for " + bankId);
        }
    }

    @Transactional
    public void seedFeaturesAndProducts(String bankId) {
        ProductType casaType = productTypeRepository.findByName("CASA")
                .orElseThrow(() -> new RuntimeException("CASA type not found for " + bankId));

        FeatureComponent maxTxn = featureComponentRepository.findByName("Max_Free_ATM_Txn")
                .orElseGet(() -> featureComponentRepository.save(createFeature("Max_Free_ATM_Txn", DataType.INTEGER, bankId)));

        Product savings = createProduct(bankId.equals(BANK_A) ? "Global Savings" : "Local Savings", casaType, bankId, "RETAIL");
        Product checking = createProduct(bankId.equals(BANK_A) ? "Global Checking" : "Local Checking", casaType, bankId, "RETAIL");

        productRepository.saveAll(List.of(savings, checking));
        linkRepository.save(createLink(savings, maxTxn, "10", bankId));
    }

    @Transactional
    public void seedPricingComponentsAndLinks(String bankId) {
        String name = "Monthly_Maintenance_Fee";
        PricingComponent fee = pricingComponentRepository.findByName(name).orElseGet(() -> {
            PricingComponent c = new PricingComponent();
            c.setName(name);
            c.setType(ComponentType.FEE);
            c.setBankId(bankId);
            return pricingComponentRepository.save(c);
        });

        PricingTier tier = createTier("Standard Tier", fee, bankId);
        tierConditionRepository.save(createCondition(tier, "customerSegment", Operator.EQ, "STANDARD", bankId));
        priceValueRepository.save(createPriceValue(new BigDecimal("15.00"), PriceValue.ValueType.FEE_ABSOLUTE, tier, bankId));

        productRepository.findByName(bankId.equals(BANK_A) ? "Global Savings" : "Local Savings").ifPresent(p -> {
            ProductPricingLink link = new ProductPricingLink();
            link.setProduct(p);
            link.setPricingComponent(fee);
            link.setBankId(bankId);
            productPricingLinkRepository.save(link);
        });
    }

    @Transactional
    public void seedBundles(String bankId) {
        Product savings = productRepository.findByName(bankId.equals(BANK_A) ? "Global Savings" : "Local Savings")
                .orElseThrow(() -> new RuntimeException("Savings not found for " + bankId));
        Product checking = productRepository.findByName(bankId.equals(BANK_A) ? "Global Checking" : "Local Checking")
                .orElseThrow(() -> new RuntimeException("Checking not found for " + bankId));

        ProductBundle bundle = new ProductBundle();
        bundle.setBankId(bankId);
        bundle.setCode(bankId + "-BNDL-01");
        bundle.setName(bankId.equals(BANK_A) ? "Gold Elite Bundle" : "Standard Starter Pack");
        bundle.setDescription("Comprehensive bundle for " + bankId);
        bundle.setStatus(ProductBundle.BundleStatus.ACTIVE);
        bundle.setActivationDate(LocalDate.now());
        bundle.setEligibilitySegment("RETAIL");

        List<BundleProductLink> links = new ArrayList<>();
        links.add(createBundleLink(bundle, savings, true, true, bankId));
        links.add(createBundleLink(bundle, checking, false, false, bankId));

        bundle.setContainedProducts(links);
        productBundleRepository.save(bundle);
        System.out.println("Seeded Product Bundle for " + bankId);
    }

    // --- Helpers ---

    private BundleProductLink createBundleLink(ProductBundle b, Product p, boolean isMain, boolean isMandatory, String bankId) {
        BundleProductLink link = new BundleProductLink();
        link.setProductBundle(b);
        link.setProduct(p);
        link.setMainAccount(isMain);
        link.setMandatory(isMandatory);
        link.setBankId(bankId);
        return link;
    }

    private ProductType createType(String name, String bankId) {
        ProductType t = new ProductType(); t.setName(name); t.setBankId(bankId); return t;
    }

    private Product createProduct(String name, ProductType type, String bankId, String cat) {
        Product p = new Product();
        p.setName(name); p.setProductType(type); p.setBankId(bankId);
        p.setCategory(cat); p.setEffectiveDate(LocalDate.now()); p.setStatus("ACTIVE");
        return p;
    }

    private FeatureComponent createFeature(String name, DataType type, String bankId) {
        FeatureComponent f = new FeatureComponent(); f.setName(name); f.setDataType(type); f.setBankId(bankId); return f;
    }

    private ProductFeatureLink createLink(Product p, FeatureComponent f, String v, String bankId) {
        ProductFeatureLink l = new ProductFeatureLink(); l.setProduct(p); l.setFeatureComponent(f); l.setFeatureValue(v); l.setBankId(bankId); return l;
    }

    private PricingTier createTier(String name, PricingComponent c, String bankId) {
        PricingTier t = new PricingTier(); t.setTierName(name + " " + bankId); t.setPricingComponent(c); t.setBankId(bankId); return pricingTierRepository.save(t);
    }

    private TierCondition createCondition(PricingTier t, String a, Operator o, String v, String bankId) {
        TierCondition cond = new TierCondition(); cond.setPricingTier(t); cond.setAttributeName(a); cond.setOperator(o); cond.setAttributeValue(v); cond.setBankId(bankId); return cond;
    }

    private PriceValue createPriceValue(BigDecimal amt, PriceValue.ValueType type, PricingTier t, String bankId) {
        PriceValue v = new PriceValue(); v.setRawValue(amt); v.setValueType(type); v.setPricingTier(t); v.setBankId(bankId); return v;
    }
}