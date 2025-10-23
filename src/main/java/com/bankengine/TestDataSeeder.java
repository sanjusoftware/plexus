package com.bankengine;

import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.FeatureComponent.DataType;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.catalog.repository.FeatureComponentRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingComponent.ComponentType;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.ProductPricingLink;
import com.bankengine.pricing.repository.PriceValueRepository;
import com.bankengine.pricing.repository.PricingComponentRepository;

import com.bankengine.pricing.repository.PricingTierRepository;
import com.bankengine.pricing.repository.ProductPricingLinkRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
@Profile("dev") // <-- Ensures this code only runs when the 'dev' profile is active
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

    public TestDataSeeder(
            ProductTypeRepository productTypeRepository,
            FeatureComponentRepository featureComponentRepository,
            ProductRepository productRepository,
            ProductFeatureLinkRepository linkRepository,
            PricingComponentRepository pricingComponentRepository,
            PricingTierRepository pricingTierRepository,
            PriceValueRepository priceValueRepository,
            ProductPricingLinkRepository productPricingLinkRepository) {
        this.productTypeRepository = productTypeRepository;
        this.featureComponentRepository = featureComponentRepository;
        this.productRepository = productRepository;
        this.linkRepository = linkRepository;
        this.pricingComponentRepository = pricingComponentRepository;
        this.pricingTierRepository = pricingTierRepository;
        this.priceValueRepository = priceValueRepository;
        this.productPricingLinkRepository = productPricingLinkRepository;
    }

    @Override
    public void run(String... args) {
        System.out.println("--- Seeding Development Data ---");
        seedProductTypes();
        seedFeaturesAndProducts();
        seedPricingComponents();
        System.out.println("--- Seeding Complete ---");
    }

    private void seedProductTypes() {
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

    private void seedFeaturesAndProducts() {
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

    private void seedPricingComponents() {

        // --- 1. Seed Component: Annual Card Fee ---
        PricingComponent annualFee = new PricingComponent();
        annualFee.setName("Annual_Credit_Card_Fee");
        annualFee.setType(ComponentType.FEE);
        PricingComponent savedFeeComponent = pricingComponentRepository.save(annualFee);

        // --- 2. Create Tier 1: High Net Worth (Waived Fee) ---
        PricingTier tierHnw = new PricingTier();
        tierHnw.setTierName("High Net Worth Waiver");
        tierHnw.setConditionKey("Customer_Segment"); // Placeholder key
        tierHnw.setConditionValue("HNW"); // Placeholder value
        tierHnw.setPricingComponent(savedFeeComponent);
        PricingTier savedTierHnw = pricingTierRepository.save(tierHnw);

        PriceValue valueHnw = new PriceValue();
        valueHnw.setPriceAmount(BigDecimal.ZERO);
        valueHnw.setValueType(PriceValue.ValueType.WAIVED); // Fee is waived
        valueHnw.setCurrency("USD");
        valueHnw.setPricingTier(savedTierHnw);
        priceValueRepository.save(valueHnw); // <-- Saves the linked PriceValue

        // --- 3. Create Tier 2: Standard Client ($50 Fee) ---
        // Find the component to link the tier to
        PricingComponent standardFeeComponent = pricingComponentRepository.findByName("Annual_Credit_Card_Fee")
                .orElseThrow(() -> new RuntimeException("Fee component not found."));
        PricingTier tierStandard = new PricingTier();
        tierStandard.setTierName("Standard Client Fee");
        tierStandard.setConditionKey("Annual_Spend"); // Setting the context for the threshold
        tierStandard.setConditionValue("STANDARD");
        tierStandard.setMinThreshold(new BigDecimal("0.00"));
        tierStandard.setMaxThreshold(new BigDecimal("10000.00")); // Max annual spend of $10,000

        tierStandard.setPricingComponent(standardFeeComponent);
        PricingTier savedTierStandard = pricingTierRepository.save(tierStandard);

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