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

    private static final String BANK_A = "GLOBAL-BANK-001";
    private static final String BANK_B = "LOCAL-BANK-002";

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
    private final ApplicationContext applicationContext;

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
            ApplicationContext applicationContext) {
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
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(String... args) {
        System.out.println("--- Seeding Development Data ---");

        TestDataSeeder proxy = applicationContext.getBean(TestDataSeeder.class);

        proxy.seedTestRoles();
        proxy.seedProductTypes();
        proxy.seedPricingInputMetadata();

        // --- Seed Data for BANK A ---
        System.out.println("\n--- Seeding Data for " + BANK_A + " ---");
        proxy.seedFeaturesAndProducts(BANK_A);
        proxy.seedPricingComponentsAndLinks(BANK_A);
        proxy.seedBundlePricingRules(BANK_A); // <-- Added for consistency

        // --- Seed Data for BANK B ---
        System.out.println("\n--- Seeding Data for " + BANK_B + " ---");
        proxy.seedFeaturesAndProducts(BANK_B);
        proxy.seedPricingComponentsAndLinks(BANK_B);
        proxy.seedBundlePricingRules(BANK_B);

        System.out.println("\n--- Seeding Complete ---");
    }

    @Transactional
    public void seedTestRoles() {
        System.out.println("Seeding Application Roles...");

        // The exhaustive list of all system authorities
        final Set<String> ALL_AUTHORITIES = Set.of(
                "pricing:metadata:read", "catalog:product:activate", "pricing:metadata:create",
                "pricing:metadata:update", "pricing:tier:create", "pricing:component:create",
                "pricing:metadata:delete", "catalog:product:update", "catalog:feature:create",
                "catalog:product-type:read", "catalog:product:read", "pricing:component:delete",
                "pricing:tier:delete", "pricing:tier:update", "auth:role:write",
                "catalog:feature:read", "pricing:component:read", "catalog:feature:delete",
                "pricing:component:update", "catalog:product:deactivate", "catalog:product:create",
                "pricing:calculate", "catalog:feature:update", "auth:role:read",
                "catalog:product-type:create",
                "pricing:bundle:calculate:read"
        );

        // --- 1. Define Permissions Sets ---

        // Read Permissions (for Analyst role)
        Set<String> readPermissions = ALL_AUTHORITIES.stream()
                .filter(p -> p.endsWith(":read"))
                .collect(Collectors.toSet());
        readPermissions.add("pricing:calculate");
        readPermissions.add("pricing:bundle:calculate:read");

        // Pricing Permissions (for Pricing Engineer)
        Set<String> pricingPermissions = ALL_AUTHORITIES.stream()
                .filter(p -> p.startsWith("pricing:") && !p.endsWith(":read"))
                .collect(Collectors.toSet());
        pricingPermissions.addAll(Set.of(
                "pricing:component:read",
                "pricing:metadata:read",
                "pricing:calculate",
                "pricing:bundle:calculate:read"
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

        List<Role> rolesToSeed = List.of(
                createRole("SUPER_ADMIN", ALL_AUTHORITIES), // Full control
                createRole("PRICING_ENGINEER", pricingPermissions),
                createRole("PRODUCT_MANAGER", ALL_AUTHORITIES.stream().filter(p -> p.startsWith("catalog:")).collect(Collectors.toSet())),
                createRole("AUTH_MANAGER", ALL_AUTHORITIES.stream().filter(p -> p.startsWith("auth:")).collect(Collectors.toSet())),
                createRole("ANALYST", readPermissions)
        );

        rolesToSeed.stream()
                .filter(role -> roleRepository.findByName(role.getName()).isEmpty())
                .forEach(roleRepository::save);

        System.out.println("Seeded or ensured existence of 5 application roles. Added bundle authority.");
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
                    createMetadata("transactionAmount", "Transaction Amount", "DECIMAL"),
                    createMetadata("productId", "Product ID", "LONG"),
                    createMetadata("bankId", "Bank ID", "STRING")
            ));
        }
    }

    private ProductType createType(String name) {
        ProductType type = new ProductType();
        type.setName(name);
        return type;
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


    /**
     * Seeds features and products scoped to a specific bankId.
     */
    @Transactional
    public void seedFeaturesAndProducts(String bankId) {
        // --- 1. Get Product Types for Foreign Keys ---
        ProductType casaType = productTypeRepository.findByName("CASA")
                .orElseThrow(() -> new RuntimeException("CASA type not found."));

        ProductType packageType = productTypeRepository.findByName("Package")
                .orElseThrow(() -> new RuntimeException("Package type not found."));

        // --- 2. Seed Feature Components (Tenant-Agnostic) ---
        FeatureComponent maxTxn = featureComponentRepository.findByName("Max_Free_ATM_Txn").orElseGet(() -> featureComponentRepository.save(createFeature("Max_Free_ATM_Txn", DataType.INTEGER)));
        FeatureComponent minBalance = featureComponentRepository.findByName("Minimum_Balance").orElseGet(() -> featureComponentRepository.save(createFeature("Minimum_Balance", DataType.DECIMAL)));
        featureComponentRepository.saveAll(List.of(maxTxn, minBalance));

        // --- 3. Seed Products ---
        Product savingsAccount = new Product();
        savingsAccount.setName(bankId.equals(BANK_A) ? "Global Savings Account" : "Local Savings Account");
        savingsAccount.setBankId(bankId);
        savingsAccount.setProductType(casaType);
        savingsAccount.setEffectiveDate(LocalDate.now());
        savingsAccount.setStatus("ACTIVE");
        productRepository.save(savingsAccount);

        Product checkingAccount = new Product();
        checkingAccount.setName(bankId.equals(BANK_A) ? "Global Checking Account" : "Local Checking Account");
        checkingAccount.setBankId(bankId);
        checkingAccount.setProductType(casaType);
        checkingAccount.setEffectiveDate(LocalDate.now());
        checkingAccount.setStatus("ACTIVE");
        productRepository.save(checkingAccount);

        Product bundleProduct = new Product();
        bundleProduct.setName(bankId.equals(BANK_A) ? "Global Elite Bundle" : "Local Basic Bundle");
        bundleProduct.setBankId(bankId);
        bundleProduct.setProductType(packageType);
        bundleProduct.setEffectiveDate(LocalDate.now());
        bundleProduct.setStatus("ACTIVE");
        productRepository.save(bundleProduct);


        // --- 4. Link Features to Product ---
        ProductFeatureLink link1 = createLink(savingsAccount, maxTxn, bankId.equals(BANK_A) ? "10" : "5"); // Bank A gets 10, Bank B gets 5
        ProductFeatureLink link2 = createLink(savingsAccount, minBalance, "5000.00");
        linkRepository.saveAll(List.of(link1, link2));

        System.out.println("Seeded " + productRepository.countByBankId(bankId) + " products for " + bankId + ".");
    }

    /**
     * Seeds pricing components and links products to them, scoped to a specific bankId.
     */
    @Transactional
    public void seedPricingComponentsAndLinks(String bankId) {

        // --- 1. Seed Component: Monthly Fee (Shared Name, Retrieve or Create) ---
        String componentName = "Monthly_Maintenance_Fee";
        PricingComponent monthlyFee = pricingComponentRepository.findByName(componentName).orElseGet(() -> {
            PricingComponent newComponent = new PricingComponent();
            newComponent.setName(componentName);
            newComponent.setType(ComponentType.FEE);
            return pricingComponentRepository.save(newComponent);
        });
        PricingComponent savedFeeComponent = monthlyFee; // Use the found or saved component

        // --- 2. Define Pricing Tier Logic (DIFFERENT PER BANK) ---

        // --- Tier 1: Premium Client (Waived Fee) ---
        PricingTier tierPremium = new PricingTier();
        tierPremium.setTierName("Premium Waived Tier " + bankId); // Add Bank ID for unique tier names
        tierPremium.setPricingComponent(savedFeeComponent);
        PricingTier savedTierPremium = pricingTierRepository.save(tierPremium);

        // Condition: PREMIUM segment
        TierCondition condPremium = createCondition(
                savedTierPremium,
                "customerSegment",
                Operator.EQ,
                "PREMIUM"
        );
        tierConditionRepository.save(condPremium);

        PriceValue valuePremium = new PriceValue();
        valuePremium.setPriceAmount(BigDecimal.ZERO);
        valuePremium.setValueType(PriceValue.ValueType.WAIVED);
        valuePremium.setCurrency("USD");
        valuePremium.setPricingTier(savedTierPremium);
        priceValueRepository.save(valuePremium);

        // --- Tier 2: Standard Client (DIFFERENT FEE PER BANK) ---
        PricingTier tierStandard = new PricingTier();
        tierStandard.setTierName("Standard Fee Tier " + bankId); // Add Bank ID for unique tier names

        BigDecimal standardFee = bankId.equals(BANK_A) ? new BigDecimal("20.00") : new BigDecimal("10.00"); // BANK A = $20, BANK B = $10

        tierStandard.setPricingComponent(savedFeeComponent);
        PricingTier savedTierStandard = pricingTierRepository.save(tierStandard);

        // Condition: STANDARD segment (FIXED: The condition for standard segment was missing)
        TierCondition condStandard = createCondition(
                savedTierStandard,
                "customerSegment", // <-- Condition must be on customerSegment
                Operator.EQ,
                "STANDARD"
        );
        tierConditionRepository.save(condStandard);

        PriceValue valueStandard = new PriceValue();
        valueStandard.setPriceAmount(standardFee);
        valueStandard.setValueType(PriceValue.ValueType.ABSOLUTE);
        valueStandard.setCurrency("USD");
        valueStandard.setPricingTier(savedTierStandard);
        priceValueRepository.save(valueStandard);

        // --- 3. Link Product to Pricing Component ---

        // Retrieve seeded Product (e.g., Savings Account)
        Product savingsAccount = productRepository.findByName(bankId.equals(BANK_A) ? "Global Savings Account" : "Local Savings Account")
                .orElseThrow(() -> new RuntimeException("Savings Account not found for linking in " + bankId));

        // Create the Link (This link will be filtered by BankId)
        ProductPricingLink link = new ProductPricingLink();
        link.setProduct(savingsAccount);
        link.setPricingComponent(savedFeeComponent);
        link.setContext("CORE_FEE"); // Define the purpose of this link
        link.setUseRulesEngine(true);
        productPricingLinkRepository.save(link);

        System.out.println("Seeded Pricing Component '" + componentName +
                "' with two tiers, linked to '" + savingsAccount.getName() + "'.");
    }

    /**
     * Seeds a bundle pricing rule metadata component and links it to the bundle product.
     */
    @Transactional
    public void seedBundlePricingRules(String bankId) {

        // --- 1. Seed Component: Bundle Fee Waiver (Retrieve or Create) ---
        String componentName = "Annual_Bundle_Waiver";
        PricingComponent bundleWaiver = pricingComponentRepository.findByName(componentName).orElseGet(() -> {
            PricingComponent newComponent = new PricingComponent();
            newComponent.setName(componentName);
            newComponent.setType(ComponentType.WAIVER);
            return pricingComponentRepository.save(newComponent);
        });

        // --- 2. Simple Pricing Link (Fixed Waiver for the Bundle Product) ---

        // Retrieve seeded Bundle Product
        Product bundleProduct = productRepository.findByName(bankId.equals(BANK_A) ? "Global Elite Bundle" : "Local Basic Bundle")
                .orElseThrow(() -> new RuntimeException("Bundle Product not found for linking in " + bankId));

        // Create the Link
        ProductPricingLink link = new ProductPricingLink();
        link.setProduct(bundleProduct);
        link.setPricingComponent(bundleWaiver);
        link.setContext("BUNDLE_DISCOUNT_TARGET"); // The DRL rule will target this specific context

        // This is a fixed, simple waiver of $50, which is overridable by Drools
        link.setFixedValue(new BigDecimal("-50.00"));
        link.setUseRulesEngine(false); // Can be complex (true) or simple (false)
        productPricingLinkRepository.save(link);

        System.out.println("Seeded bundle component '" + componentName + "' linked to '" + bundleProduct.getName() + "'.");
    }
}