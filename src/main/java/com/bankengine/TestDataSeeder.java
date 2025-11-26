package com.bankengine;

import com.bankengine.auth.model.Role;
import com.bankengine.auth.repository.RoleRepository;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.FeatureComponent.DataType;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.FeatureComponentRepository;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Profile("dev")
public class TestDataSeeder implements CommandLineRunner {

    // Inject all necessary Repositories
    private final ProductTypeRepository productTypeRepository;
    private final FeatureComponentRepository featureComponentRepository;
    private final ProductRepository productRepository;
    private final ProductFeatureLinkRepository linkRepository;
    private final PricingComponentRepository pricingComponentRepository;
    private final PricingTierRepository pricingTierRepository;
    private final PriceValueRepository priceValueRepository;
    private final ProductPricingLinkRepository productPricingLinkRepository;
    private final TierConditionRepository tierConditionRepository;
    private final PricingInputMetadataRepository pricingInputMetadataRepository;
    private final RoleRepository roleRepository;
    private final ApplicationContext applicationContext; // ADDED FIELD

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
            PricingInputMetadataRepository pricingInputMetadataRepository,
            RoleRepository roleRepository,
            ApplicationContext applicationContext) { // ADDED ApplicationContext to constructor
        this.productTypeRepository = productTypeRepository;
        this.featureComponentRepository = featureComponentRepository;
        this.productRepository = productRepository;
        this.linkRepository = linkRepository;
        this.pricingComponentRepository = pricingComponentRepository;
        this.pricingTierRepository = pricingTierRepository;
        this.priceValueRepository = priceValueRepository;
        this.productPricingLinkRepository = productPricingLinkRepository;
        this.tierConditionRepository = tierConditionRepository;
        this.pricingInputMetadataRepository = pricingInputMetadataRepository;
        this.roleRepository = roleRepository;
        this.applicationContext = applicationContext; // Assign ApplicationContext
    }

    @Override
    public void run(String... args) {
        System.out.println("--- Seeding Development Data ---");

        // FIX: Get a proxy reference to *this* bean to ensure @Transactional methods are intercepted.
        TestDataSeeder proxy = applicationContext.getBean(TestDataSeeder.class);

        // Call the transactional methods via the proxy
        proxy.seedTestRoles();
        proxy.seedProductTypes();
        proxy.seedFeaturesAndProducts();
        proxy.seedPricingInputMetadata();
        proxy.seedPricingComponents();

        System.out.println("--- Seeding Complete ---");
    }

    @Transactional
    public void seedTestRoles() {
        System.out.println("Seeding Application Roles...");

        // The exhaustive list of all 25 system authorities
        final Set<String> ALL_AUTHORITIES = Set.of(
                "pricing:metadata:read", "catalog:product:activate", "pricing:metadata:create",
                "pricing:metadata:update", "pricing:tier:create", "pricing:component:create",
                "pricing:metadata:delete", "catalog:product:update", "catalog:feature:create",
                "catalog:product-type:read", "catalog:product:read", "pricing:component:delete",
                "pricing:tier:delete", "pricing:tier:update", "auth:role:write",
                "catalog:feature:read", "pricing:component:read", "catalog:feature:delete",
                "pricing:component:update", "catalog:product:deactivate", "catalog:product:create",
                "pricing:calculate", "catalog:feature:update", "auth:role:read",
                "catalog:product-type:create"
        );

        // --- 1. Define Permissions Sets ---

        // Read Permissions (for Analyst role)
        Set<String> readPermissions = ALL_AUTHORITIES.stream()
                .filter(p -> p.endsWith(":read"))
                .collect(Collectors.toSet());
        readPermissions.add("pricing:calculate");

        // Pricing Permissions (for Pricing Engineer)
        Set<String> pricingPermissions = ALL_AUTHORITIES.stream()
                .filter(p -> p.startsWith("pricing:") && !p.endsWith(":read"))
                .collect(Collectors.toSet());
        pricingPermissions.addAll(Set.of(
                "pricing:component:read",
                "pricing:metadata:read",
                "pricing:calculate"
        ));

        // Catalog Permissions (for Product Manager)
        Set<String> catalogPermissions = ALL_AUTHORITIES.stream()
                .filter(p -> p.startsWith("catalog:") && !p.endsWith(":read"))
                .collect(Collectors.toSet());
        catalogPermissions.addAll(Set.of(
                "catalog:product:read",
                "catalog:feature:read",
                "catalog:product-type:read"
        ));

        // Auth Permissions (for Auth Manager)
        Set<String> authPermissions = ALL_AUTHORITIES.stream()
                .filter(p -> p.startsWith("auth:"))
                .collect(Collectors.toSet());


        // --- 2. Helper Method to Create and Save Role ---
        List<Role> rolesToSeed = List.of(
                createRole("SUPER_ADMIN", ALL_AUTHORITIES), // Full control
                createRole("PRICING_ENGINEER", pricingPermissions),
                createRole("PRODUCT_MANAGER", catalogPermissions),
                createRole("AUTH_MANAGER", authPermissions),
                createRole("ANALYST", readPermissions)
        );

        // Filter out roles that already exist and save the rest
        rolesToSeed.stream()
                .filter(role -> roleRepository.findByName(role.getName()).isEmpty())
                .forEach(roleRepository::save);

        System.out.println("Seeded or ensured existence of 5 application roles.");
    }

    private Role createRole(String name, Set<String> authorities) {
        Role role = new Role();
        role.setName(name);
        role.setAuthorities(new HashSet<>(authorities));
        return role;
    }

    private PricingInputMetadata createMetadata(String key, String displayName, String dataType) {
        PricingInputMetadata metadata = new PricingInputMetadata();
        metadata.setAttributeKey(key);
        metadata.setDisplayName(displayName);
        metadata.setDataType(dataType);
        return metadata;
    }

    @Transactional
    public void seedPricingInputMetadata() {
        if (pricingInputMetadataRepository.count() == 0) {
            System.out.println("Seeding Pricing Input Metadata...");
            pricingInputMetadataRepository.saveAll(List.of(
                    createMetadata("customerSegment", "Client Segment", "STRING"),
                    createMetadata("transactionAmount", "Transaction Amount", "DECIMAL")
            ));
        }
    }

    @Transactional
    public void seedProductTypes() {
        if (productTypeRepository.count() == 0) {
            productTypeRepository.saveAll(List.of(
                    createType("CASA"),
                    createType("Credit Card"),
                    createType("Loan (Secured)"),
                    createType("Package")
            ));
        }
    }

    private ProductType createType(String name) {
        ProductType type = new ProductType();
        type.setName(name);
        return type;
    }

    @Transactional
    public void seedFeaturesAndProducts() {
        // --- 1. Get Product Types for Foreign Keys ---
        ProductType casaType = productTypeRepository.findByName("CASA")
                .orElseThrow(() -> new RuntimeException("CASA type not found."));

        // --- 2. Seed Feature Components ---
        FeatureComponent maxTxn = createFeature("Max_Free_ATM_Txn", DataType.INTEGER);
        FeatureComponent minBalance = createFeature("Minimum_Balance", DataType.DECIMAL);
        featureComponentRepository.saveAll(List.of(maxTxn, minBalance));

        // --- 3. Seed Product ---
        Product premiumAccount = new Product();
        premiumAccount.setName("Premium Savings Account");
        premiumAccount.setBankId("GLOBAL-BANK-001");
        premiumAccount.setProductType(casaType);
        premiumAccount.setEffectiveDate(LocalDate.now());
        premiumAccount.setStatus("ACTIVE");
        productRepository.save(premiumAccount);

        // --- 4. Link Features to Product ---
        ProductFeatureLink link1 = createLink(premiumAccount, maxTxn, "10"); // 10 free txns
        ProductFeatureLink link2 = createLink(premiumAccount, minBalance, "5000.00"); // $5000 min
        linkRepository.saveAll(List.of(link1, link2));
    }

    private FeatureComponent createFeature(String name, DataType type) {
        FeatureComponent feature = new FeatureComponent();
        feature.setName(name);
        feature.setDataType(type);
        return feature;
    }

    private ProductFeatureLink createLink(Product product, FeatureComponent feature, String value) {
        ProductFeatureLink link = new ProductFeatureLink();
        link.setProduct(product);
        link.setFeatureComponent(feature);
        link.setFeatureValue(value);
        return link;
    }

    private TierCondition createCondition(PricingTier tier, String attribute, Operator operator, String value) {
        TierCondition condition = new TierCondition();
        condition.setPricingTier(tier);
        condition.setAttributeName(attribute);
        condition.setOperator(operator);
        condition.setAttributeValue(value);
        return condition;
    }

    @Transactional
    public void seedPricingComponents() {

        // --- 1. Seed Component: Annual Card Fee ---
        PricingComponent annualFee = new PricingComponent();
        annualFee.setName("Annual_Credit_Card_Fee");
        annualFee.setType(ComponentType.FEE);
        PricingComponent savedFeeComponent = pricingComponentRepository.save(annualFee);

        // --- 2. Create Tier 1: High Net Worth (Waived Fee) ---
        PricingTier tierPremium = new PricingTier();
        tierPremium.setTierName("Premium Tier");
        tierPremium.setPricingComponent(savedFeeComponent);
        PricingTier savedTierPremium = pricingTierRepository.save(tierPremium);

        TierCondition condPremium = createCondition(
                savedTierPremium,
                "customerSegment",
                Operator.EQ,
                "PREMIUM"
        );
        tierConditionRepository.save(condPremium);

        PriceValue valuePremium = new PriceValue();
        valuePremium.setPriceAmount(BigDecimal.ZERO);
        valuePremium.setValueType(PriceValue.ValueType.WAIVED); // Fee is waived
        valuePremium.setCurrency("USD");
        valuePremium.setPricingTier(savedTierPremium);
        priceValueRepository.save(valuePremium); // <-- Saves the linked PriceValue

        // --- 3. Create Tier 2: Standard Client ($50 Fee) ---
        // Find the component to link the tier to
        PricingComponent standardFeeComponent = pricingComponentRepository.findByName("Annual_Credit_Card_Fee")
                .orElseThrow(() -> new RuntimeException("Fee component not found."));
        PricingTier tierStandard = new PricingTier();
        tierStandard.setTierName("Standard Tier");
        tierStandard.setMinThreshold(new BigDecimal("0.00"));
        tierStandard.setMaxThreshold(new BigDecimal("10000.00")); // Max annual spend of $10,000

        tierStandard.setPricingComponent(standardFeeComponent);
        PricingTier savedTierStandard = pricingTierRepository.save(tierStandard);

        TierCondition condStandard = createCondition(
                savedTierStandard,
                "transactionAmount",
                Operator.LE,
                "10000.00"
        );
        tierConditionRepository.save(condStandard);
        savedTierStandard.setConditions(java.util.Set.of(condStandard)); // Update in-memory set

        PriceValue valueStandard = new PriceValue();
        valueStandard.setPriceAmount(new BigDecimal("50.00"));
        valueStandard.setValueType(PriceValue.ValueType.ABSOLUTE);
        valueStandard.setCurrency("USD");
        valueStandard.setPricingTier(savedTierStandard);
        priceValueRepository.save(valueStandard); // <-- Saves the linked PriceValue

        // --- 4. Link Product to Pricing Component ---

        // Retrieve seeded Product ("Premium Savings Account")
        Product premiumAccount = productRepository.findByName("Premium Savings Account")
                .orElseThrow(() -> new RuntimeException("Product not found for linking."));

        // Retrieve seeded Pricing Component ("Annual_Credit_Card_Fee")
        PricingComponent annualFeeComponent = pricingComponentRepository.findByName("Annual_Credit_Card_Fee")
                .orElseThrow(() -> new RuntimeException("Pricing Component not found for linking."));

        // Create the Link
        ProductPricingLink link = new ProductPricingLink();
        link.setProduct(premiumAccount);
        link.setPricingComponent(annualFeeComponent);
        link.setContext("CORE_FEE"); // Define the purpose of this link

        productPricingLinkRepository.save(link);

        System.out.println("Linked Product '" + premiumAccount.getName() +
                "' to Pricing Component '" + annualFeeComponent.getName() + "'.");
    }
}