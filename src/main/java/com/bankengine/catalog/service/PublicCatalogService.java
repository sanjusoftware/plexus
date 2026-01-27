package com.bankengine.catalog.service;

import com.bankengine.catalog.converter.ProductMapper;
import com.bankengine.catalog.dto.BundleCatalogCard;
import com.bankengine.catalog.dto.ProductCatalogCard;
import com.bankengine.catalog.dto.ProductComparisonView;
import com.bankengine.catalog.dto.ProductDetailView;
import com.bankengine.catalog.model.BundleProductLink;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.repository.ProductBundleRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.common.service.BaseService;
import com.bankengine.pricing.dto.BundlePriceRequest;
import com.bankengine.pricing.dto.BundlePriceResponse;
import com.bankengine.pricing.dto.PricingRequest;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.ProductPricingLink;
import com.bankengine.pricing.service.BundlePricingService;
import com.bankengine.pricing.service.PricingCalculationService;
import com.bankengine.web.exception.NotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PublicCatalogService extends BaseService {

    private final ProductRepository productRepository;
    private final ProductBundleRepository productBundleRepository;
    private final ProductMapper productMapper;
    private final PricingCalculationService pricingCalculationService;
    private final BundlePricingService bundlePricingService;

    public PublicCatalogService(ProductRepository productRepository, ProductBundleRepository productBundleRepository, ProductMapper productMapper, PricingCalculationService pricingCalculationService, BundlePricingService bundlePricingService) {
        this.productRepository = productRepository;
        this.productBundleRepository = productBundleRepository;
        this.productMapper = productMapper;
        this.pricingCalculationService = pricingCalculationService;
        this.bundlePricingService = bundlePricingService;
    }

    @Cacheable(value = "publicCatalog",
            key = "T(com.bankengine.auth.security.TenantContextHolder).getBankId() + '_' + #category + '_' + #productTypeId + '_' + #customerSegment + '_' + #pageable.pageNumber")
    public Page<ProductCatalogCard> getActiveProducts(
            String category,
            Long productTypeId,
            String customerSegment,
            Pageable pageable) {

        // Build specification for ACTIVE products available today
        Specification<Product> spec = Specification.<Product>where(
                (root, query, cb) -> cb.equal(root.get("status"), "ACTIVE")
        ).and(
                (root, query, cb) -> cb.lessThanOrEqualTo(root.get("effectiveDate"), LocalDate.now())
        ).and(
                (root, query, cb) -> cb.or(
                        cb.isNull(root.get("expirationDate")),
                        cb.greaterThan(root.get("expirationDate"), LocalDate.now()))
        );

        // Apply filters
        if (category != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), category));
        }

        if (productTypeId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("productType").get("id"), productTypeId));
        }

        if (customerSegment != null) {
            spec = spec.and((root, query, cb) ->
                    cb.like(root.get("targetCustomerSegments"), "%" + customerSegment + "%")
            );
        }
        Page<Product> products = productRepository.findAll(spec, pageable);
        return products.map(this::toProductCatalogCard);
    }

    @Cacheable(value = "productDetails",
            key = "T(com.bankengine.auth.security.TenantContextHolder).getBankId() + '_' + #productId")
    public ProductDetailView getProductDetailView(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found: " + productId));

        // Verify product is customer-viewable
        if (!"ACTIVE".equals(product.getStatus())) {
            throw new NotFoundException("Product is not currently available");
        }

        return ProductDetailView.builder()
                .productId(product.getId())
                .productName(product.getName())
                .fullDescription(product.getFullDescription())
                .category(product.getCategory())
                .featuresByCategory(organizeFeaturesByCategory(product))
                .pricing(buildPricingBreakdown(product))
                .termsAndConditions(product.getTermsAndConditions())
                .availableFrom(product.getEffectiveDate())
                .availableUntil(product.getExpirationDate())
                .relatedProducts(findRelatedProducts(product))
                .build();
    }

    public List<ProductCatalogCard> getRecommendedProducts(
            String customerSegment,
            BigDecimal estimatedMonthlyBalance) {

        // 1. Fetch active products filtered by the customer segment
        Specification<Product> spec = Specification.<Product>where(
                (root, query, cb) -> cb.equal(root.get("status"), "ACTIVE")
        ).and(
                (root, query, cb) -> cb.like(root.get("targetCustomerSegments"), "%" + customerSegment + "%")
        );

        List<Product> products = productRepository.findAll(spec);

        // 2. Use pricingCalculationService to personalize the "price" for each product
        return products.stream()
                .map(product -> {
                    ProductCatalogCard card = toProductCatalogCard(product);

                    if (estimatedMonthlyBalance != null) {
                        // Simulate a pricing calculation based on the user's balance
                        // This is where the Drools engine/Pricing Service logic from your patch kicks in
                        PricingRequest request = new PricingRequest();
                        request.setProductId(product.getId());
                        request.setAmount(estimatedMonthlyBalance);

                        try {
                            var calculation = pricingCalculationService.getProductPricing(request);

                            // Update the card with the personalized result
                            card.getPricingSummary().setMainPriceValue(calculation.getFinalChargeablePrice());
                            card.getPricingSummary().setPriceDescription("Personalized for your balance");
                        } catch (Exception e) {
                            // Fallback if calculation fails
                            card.setEligibilityMessage("Pricing currently unavailable");
                        }
                    }

                    return card;
                })
                // Sort by lowest price first as a "recommendation"
                .sorted(java.util.Comparator.comparing(c -> c.getPricingSummary().getMainPriceValue()))
                .limit(3)
                .toList();
    }

    public BundleCatalogCard getPublicBundleDetails(Long bundleId, String segment) {
        // 1. Fetch the technical bundle structure
        ProductBundle bundle = productBundleRepository.findById(bundleId).orElseThrow();

        // 2. Build the request for the Pricing Service
        BundlePriceRequest pricingRequest = BundlePriceRequest.builder()
                .productBundleId(bundleId)
                .customerSegment(segment)
                .products(bundle.getContainedProducts().stream()
                        .map(link -> new BundlePriceRequest.ProductRequest(link.getProduct().getId(), BigDecimal.ZERO))
                        .toList())
                .build();

        // 3. Get the pricing breakdown
        BundlePriceResponse pricing = bundlePricingService.calculateTotalBundlePrice(pricingRequest);

        // 4. Extract labels for "Benefits" display
        List<String> benefits = pricing.getBundleAdjustments().stream()
                .filter(adj -> adj.getValueType().name().contains("DISCOUNT") || adj.getValueType().name().contains("WAIVER"))
                .map(adj -> adj.getComponentCode().replace("_", " "))
                .toList();

        return BundleCatalogCard.builder()
                .bundleId(bundle.getId())
                .name(bundle.getName())
                .description(bundle.getDescription())
                .items(mapItems(bundle.getContainedProducts()))
                .pricing(BundleCatalogCard.BundlePricingSummary.builder()
                        .totalMonthlyFee(pricing.getNetTotalAmount())
                        .totalSavings(pricing.getGrossTotalAmount().subtract(pricing.getNetTotalAmount()).abs())
                        .adjustmentLabels(benefits)
                        .build())
                .build();
    }

    public ProductComparisonView compareProducts(List<Long> productIds) {
        List<Product> products = productRepository.findAllById(productIds);

        // Build comparison matrix
        return ProductComparisonView.builder()
                .products(products.stream().map(this::toProductCatalogCard).toList())
                .featureComparison(buildFeatureComparisonMatrix(products))
                .pricingComparison(buildPricingComparisonMatrix(products))
                .build();
    }

    private List<BundleCatalogCard.BundleItemDetail> mapItems(List<BundleProductLink> links) {
        if (links == null) return List.of();

        return links.stream()
                .map(link -> BundleCatalogCard.BundleItemDetail.builder()
                        .productName(link.getProduct().getName())
                        .productCategory(link.getProduct().getCategory())
                        .isMandatory(link.isMandatory())
                        .isMainAccount(link.isMainAccount())
                        .build())
                .toList();
    }

    private List<ProductCatalogCard> findRelatedProducts(Product product) {
        // Find up to 3 active products in the same category, excluding itself
        Specification<Product> spec = Specification.where(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("category"), product.getCategory()),
                        cb.equal(root.get("status"), "ACTIVE"),
                        cb.notEqual(root.get("id"), product.getId())
                )
        );

        return productRepository.findAll(spec, Pageable.ofSize(3))
                .getContent()
                .stream()
                .map(this::toProductCatalogCard)
                .toList();
    }

    private Map<String, List<String>> buildFeatureComparisonMatrix(List<Product> products) {
        // 1. Get unique set of all feature names across all selected products
        Set<String> allFeatureNames = products.stream()
                .flatMap(p -> p.getProductFeatureLinks().stream())
                .map(link -> link.getFeatureComponent().getName())
                .collect(Collectors.toSet());

        // 2. Map each feature name to a list of values (one per product)
        return allFeatureNames.stream().collect(Collectors.toMap(
                name -> name,
                name -> products.stream().map(p ->
                        p.getProductFeatureLinks().stream()
                                .filter(l -> l.getFeatureComponent().getName().equals(name))
                                .map(com.bankengine.catalog.model.ProductFeatureLink::getFeatureValue)
                                .findFirst()
                                .orElse("—") // Display dash if feature is missing
                ).toList()
        ));
    }

    private Map<String, List<String>> buildPricingComparisonMatrix(List<Product> products) {
        Set<String> pricingNames = products.stream()
                .flatMap(p -> p.getProductPricingLinks().stream())
                .map(link -> link.getPricingComponent().getName())
                .collect(Collectors.toSet());

        return pricingNames.stream().collect(Collectors.toMap(
                name -> name,
                name -> products.stream().map(p ->
                        p.getProductPricingLinks().stream()
                                .filter(l -> l.getPricingComponent().getName().equals(name))
                                .map(l -> l.getFixedValue() != null ? "$" + l.getFixedValue() : "Included")
                                .findFirst()
                                .orElse("N/A")
                ).toList()
        ));
    }

    private ProductDetailView.PricingBreakdown buildPricingBreakdown(Product product) {
        List<ProductDetailView.PricingBreakdown.PricingItem> fees = new java.util.ArrayList<>();
        List<ProductDetailView.PricingBreakdown.PricingItem> rates = new java.util.ArrayList<>();
        List<ProductDetailView.PricingBreakdown.PricingItem> waivers = new java.util.ArrayList<>();

        product.getProductPricingLinks().forEach(link -> {
            PricingComponent component = link.getPricingComponent();
            String displayValue = formatPricingValue(link, component);

            var item = ProductDetailView.PricingBreakdown.PricingItem.builder()
                    .name(component.getName())
                    .value(displayValue)
                    .condition(component.getDescription())
                    .highlighted(component.getType() == PricingComponent.ComponentType.FEE || component.getType() == PricingComponent.ComponentType.PACKAGE_FEE)
                    .build();

            // Categorize based on type properly mapped to UI groupings
            switch (component.getType()) {
                case FEE, PACKAGE_FEE -> fees.add(item);
                case INTEREST_RATE -> rates.add(item);
                case DISCOUNT, WAIVER, BENEFIT -> waivers.add(item);
                default -> fees.add(item); // Fallback for other types
            }
        });

        return ProductDetailView.PricingBreakdown.builder()
                .fees(fees)
                .rates(rates)
                .waivers(waivers)
                .pricingNote("Standard rates apply. See terms for details.")
                .build();
    }

    /**
     * Helper to ensure we don't put a '$' sign in front of a 5% interest rate!
     */
    private String formatPricingValue(ProductPricingLink link, PricingComponent component) {
        if (link.getFixedValue() == null) {
            return "Variable"; // Or component.getName() if you prefer
        }

        return switch (component.getType()) {
            case INTEREST_RATE -> link.getFixedValue().toString() + "% p.a.";
            case FEE, PACKAGE_FEE -> "$" + link.getFixedValue().toString();
            case DISCOUNT, WAIVER, BENEFIT -> link.getFixedValue().toString();
            default -> link.getFixedValue().toString();
        };
    }

    private ProductCatalogCard toProductCatalogCard(Product product) {
        // 1. Map the standard fields (ID, Name, Tagline, etc.)
        ProductCatalogCard card = productMapper.toCatalogCard(product);

        // 2. Decorate with the high-value logic
        card.setKeyFeatures(product.getProductFeatureLinks().stream()
                .limit(5)
                .map(link -> ProductCatalogCard.FeatureHighlight.builder()
                        .featureName(link.getFeatureComponent().getName())
                        .displayValue(link.getFeatureValue())
                        .build())
                .toList());

        card.setPricingSummary(summarizePricing(product));

        return card;
    }

    private ProductCatalogCard.PricingSummary summarizePricing(Product product) {
        // Get the main fee (e.g., monthly maintenance fee)
        ProductPricingLink mainFeeLink = product.getProductPricingLinks().stream()
                .filter(link -> link.getPricingComponent().getType() == PricingComponent.ComponentType.FEE)
                .findFirst()
                .orElse(null);

        if (mainFeeLink == null) {
            return ProductCatalogCard.PricingSummary.builder()
                    .mainPriceLabel("No monthly fee")
                    .mainPriceValue(BigDecimal.ZERO)
                    .build();
        }

        return ProductCatalogCard.PricingSummary.builder()
                .mainPriceLabel(mainFeeLink.getPricingComponent().getName())
                .mainPriceValue(mainFeeLink.getFixedValue())
                .priceDescription("Conditions may apply")
                .build();
    }

    private Map<String, List<ProductDetailView.ProductFeatureDetail>> organizeFeaturesByCategory(Product product) {
        return product.getProductFeatureLinks().stream()
                .map(link -> ProductDetailView.ProductFeatureDetail.builder()
                        .featureName(link.getFeatureComponent().getName())
                        .value(link.getFeatureValue())
                        .displayCategory(categorizeFeature(link.getFeatureComponent()))
                        .build())
                .collect(Collectors.groupingBy(ProductDetailView.ProductFeatureDetail::getDisplayCategory));
    }

    private String categorizeFeature(FeatureComponent component) {
        // Simple categorization logic - enhance as needed
        String name = component.getName().toLowerCase();
        if (name.contains("limit") || name.contains("maximum") || name.contains("minimum")) {
            return "Account Limits";
        } else if (name.contains("rate") || name.contains("interest")) {
            return "Interest & Returns";
        } else if (name.contains("atm") || name.contains("transfer") || name.contains("service")) {
            return "Services & Access";
        }
        return "Other Features";
    }
}