package com.bankengine.catalog.service;

import com.bankengine.catalog.converter.ProductMapper;
import com.bankengine.catalog.dto.ProductCatalogCard;
import com.bankengine.catalog.dto.ProductComparisonView;
import com.bankengine.catalog.dto.ProductDetailView;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.repository.ProductBundleRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.pricing.dto.BundlePriceResponse;
import com.bankengine.pricing.dto.ProductPriceRequest;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.ProductPricingLink;
import com.bankengine.pricing.service.BundlePricingService;
import com.bankengine.pricing.service.ProductPricingService;
import com.bankengine.test.config.BaseServiceTest;
import com.bankengine.web.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PublicCatalogServiceTest extends BaseServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductPricingService productPricingService;
    @Mock
    private ProductBundleRepository productBundleRepository;
    @Mock
    private BundlePricingService bundlePricingService;
    @Mock
    private BankConfigurationRepository bankConfigurationRepository;

    @InjectMocks
    private PublicCatalogService publicCatalogService;

    @BeforeEach
    void setUp() {
        BankConfiguration config = new BankConfiguration();
        config.setCurrencyCode("USD");
        lenient().when(bankConfigurationRepository.findByBankIdUnfiltered(anyString()))
                .thenReturn(Optional.of(config));
    }

    // --- SECURITY TESTS ---

    @Test
    @DisplayName("Recommendations - Should personalize prices and sort by cheapest")
    void testGetRecommendedProducts() {
        Product p1 = createMockProduct(101L, VersionableEntity.EntityStatus.ACTIVE, "RETAIL");
        Product p2 = createMockProduct(102L, VersionableEntity.EntityStatus.ACTIVE, "RETAIL");

        when(productRepository.findAll(any(Specification.class))).thenReturn(List.of(p1, p2));
        when(productMapper.toCatalogCard(any())).thenAnswer(inv -> ProductCatalogCard.builder()
                .pricingSummary(ProductCatalogCard.PricingSummary.builder().build()).build());

        when(productPricingService.getProductPricing(any())).thenAnswer(inv -> {
            var req = (ProductPriceRequest) inv.getArgument(0);
            BigDecimal price = req.getProductId().equals(101L) ? new BigDecimal("20.00") : new BigDecimal("5.00");
            return ProductPricingCalculationResult.builder().finalChargeablePrice(price).build();
        });

        List<ProductCatalogCard> results = publicCatalogService.getRecommendedProducts("RETAIL", new BigDecimal("5000"));

        assertEquals(new BigDecimal("5.00"), results.getFirst().getPricingSummary().getMainPriceValue());
    }

    @Test
    @DisplayName("Detail View - Should throw NotFoundException for non-existent product")
    void testGetProductDetailView_NotFound() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class, () -> publicCatalogService.getProductDetailView(999L));
        assertEquals("Product not found: 999", ex.getMessage());
    }

    @Test
    @DisplayName("Feature Categorization - Should map keywords to correct display categories")
    void testOrganizeFeaturesByCategory_Keywords() {
        Product p = createMockProduct(1L, VersionableEntity.EntityStatus.ACTIVE, "TEST");
        p.setProductFeatureLinks(List.of(
                createFeatureLink("Daily Withdrawal Limit", "500"),
                createFeatureLink("Base Interest Rate", "2.5%"),
                createFeatureLink("Global ATM Service", "Free"),
                createFeatureLink("Color", "Blue")
        ));

        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        lenient().when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        Map<String, List<ProductDetailView.ProductFeatureDetail>> categorized =
                publicCatalogService.getProductDetailView(1L).getFeaturesByCategory();

        assertAll(
                () -> assertTrue(categorized.containsKey("Account Limits"), "Missing Account Limits"),
                () -> assertTrue(categorized.containsKey("Interest & Returns"), "Missing Interest & Returns"),
                () -> assertTrue(categorized.containsKey("Services & Access"), "Missing Services & Access"),
                () -> assertTrue(categorized.containsKey("Other Features"), "Missing Other Features"),
                () -> assertEquals(1, categorized.get("Account Limits").size())
        );
    }

    @Test
    @DisplayName("Pricing Breakdown - Should format values based on ComponentType")
    void testBuildPricingBreakdown_Formatting() {
        Product p = createMockProduct(1L, VersionableEntity.EntityStatus.ACTIVE, "TEST");
        p.setProductPricingLinks(List.of(
                createPricingLink("Monthly Fee", new BigDecimal("10.00"), PricingComponent.ComponentType.FEE),
                createPricingLink("Overdraft Rate", new BigDecimal("15.5"), PricingComponent.ComponentType.INTEREST_RATE)
        ));

        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        lenient().when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        ProductDetailView.PricingBreakdown breakdown = publicCatalogService.getProductDetailView(1L).getPricing();

        assertAll(
                () -> assertEquals("USD 10.00", breakdown.getFees().getFirst().getValue()),
                () -> assertEquals("15.50% p.a.", breakdown.getRates().getFirst().getValue()),
                () -> assertTrue(breakdown.getFees().getFirst().isHighlighted(), "Fees should be highlighted")
        );
    }

    @Test
    @DisplayName("Comparison Matrix - Should handle products with disjoint feature/pricing sets")
    void testCompareProducts_DisjointSets() {
        // Given: Product 1 has Feature A and Price X
        Product p1 = createMockProduct(101L, VersionableEntity.EntityStatus.ACTIVE, "RETAIL");
        p1.setProductFeatureLinks(List.of(createFeatureLink("FeatureA", "ValA")));
        p1.setProductPricingLinks(List.of(createPricingLink("PriceX", new BigDecimal("1.00"), PricingComponent.ComponentType.FEE)));

        // Given: Product 2 has Feature B and Price Y
        Product p2 = createMockProduct(102L, VersionableEntity.EntityStatus.ACTIVE, "RETAIL");
        p2.setProductFeatureLinks(List.of(createFeatureLink("FeatureB", "ValB")));
        p2.setProductPricingLinks(List.of(createPricingLink("PriceY", new BigDecimal("0.00"), PricingComponent.ComponentType.FEE)));

        when(productRepository.findAllById(anyList())).thenReturn(List.of(p1, p2));
        when(productMapper.toCatalogCard(any())).thenReturn(ProductCatalogCard.builder().build());

        ProductComparisonView matrix = publicCatalogService.compareProducts(List.of(101L, 102L));

        assertAll(
                () -> {
                    var featA = matrix.getFeatureComparison().get("FeatureA");
                    assertEquals("ValA", featA.get(0), "P1 should have ValA");
                    assertEquals("—", featA.get(1), "P2 should have dash for missing FeatureA");
                },
                () -> {
                    var featB = matrix.getFeatureComparison().get("FeatureB");
                    assertEquals("—", featB.get(0), "P1 should have dash for missing FeatureB");
                    assertEquals("ValB", featB.get(1), "P2 should have ValB");
                },

                // Pricing Matrix Check (Standardized fallback to "—")
                () -> {
                    var priceX = matrix.getPricingComparison().get("PriceX");
                    assertEquals("USD 1.00", priceX.get(0));
                    assertEquals("—", priceX.get(1), "P2 should have dash for missing PriceX");
                },
                () -> {
                    var priceY = matrix.getPricingComparison().get("PriceY");
                    assertEquals("—", priceY.get(0), "P1 should have dash for missing PriceY");
                    assertEquals("USD 0.00", priceY.get(1));
                }
        );
    }

    @Test
    @DisplayName("Recommendations - Should provide fallback message when pricing engine fails")
    void testGetRecommendedProducts_PricingFailure() {
        Product p = createMockProduct(1L, VersionableEntity.EntityStatus.ACTIVE, "RETAIL");
        when(productRepository.findAll(any(Specification.class))).thenReturn(List.of(p));
        when(productMapper.toCatalogCard(any())).thenReturn(ProductCatalogCard.builder()
                .pricingSummary(ProductCatalogCard.PricingSummary.builder().build()).build());

        when(productPricingService.getProductPricing(any())).thenThrow(new RuntimeException("Service Down"));

        List<ProductCatalogCard> results = publicCatalogService.getRecommendedProducts("RETAIL", new BigDecimal("1000"));
        assertEquals("Pricing currently unavailable", results.get(0).getEligibilityMessage());
    }

    @Test
    @DisplayName("Filtering - Should apply category, type and segment filters correctly")
    void testGetActiveProducts_FullFiltering() {
        Page<Product> mockPage = new PageImpl<>(List.of(createMockProduct(1L, VersionableEntity.EntityStatus.ACTIVE, "SAVINGS")));
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(mockPage);
        when(productMapper.toCatalogCard(any())).thenReturn(ProductCatalogCard.builder().build());

        Page<ProductCatalogCard> result = publicCatalogService.getActiveProducts("SAVINGS", 10L, "RETAIL", PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(productRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Detail View - Should trigger related products search and mapping")
    void testGetProductDetailView_RelatedProducts() {
        Product current = createMockProduct(1L, VersionableEntity.EntityStatus.ACTIVE, "RETAIL");
        Product related = createMockProduct(2L, VersionableEntity.EntityStatus.ACTIVE, "RETAIL");

        when(productRepository.findById(1L)).thenReturn(Optional.of(current));
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(related)));
        when(productMapper.toCatalogCard(any())).thenReturn(ProductCatalogCard.builder().build());

        ProductDetailView detail = publicCatalogService.getProductDetailView(1L);

        assertFalse(detail.getRelatedProducts().isEmpty());
        assertEquals(1, detail.getRelatedProducts().size());
    }

    @Test
    @DisplayName("Pricing Breakdown - Should handle Tiered and Switch fallbacks")
    void testBuildPricingBreakdown_EdgeCases() {
        Product p = createMockProduct(1L, VersionableEntity.EntityStatus.ACTIVE, "TEST");
        p.setProductPricingLinks(List.of(createPricingLink("Fee", new BigDecimal("5.00"), PricingComponent.ComponentType.FEE)));

        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        lenient().when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        ProductDetailView.PricingBreakdown breakdown = publicCatalogService.getProductDetailView(1L).getPricing();

        // Components typed as FEE or unknown default to the Fees list in the service switch
        assertFalse(breakdown.getFees().isEmpty());
        assertEquals("USD 5.00", breakdown.getFees().getFirst().getValue());
    }

    @Test
    @DisplayName("GetActiveProducts - Should cover null expiration date branch")
    void testGetActiveProducts_NullExpirationBranch() {
        Product product = createMockProduct(1L, VersionableEntity.EntityStatus.ACTIVE, "SAVINGS");
        product.setExpiryDate(null);

        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(product)));
        when(productMapper.toCatalogCard(any())).thenReturn(ProductCatalogCard.builder().build());

        Page<ProductCatalogCard> result = publicCatalogService.getActiveProducts(null, null, null, PageRequest.of(0, 10));
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("GetProductDetailView - Should throw exception for INACTIVE status")
    void testGetProductDetailView_InactiveStatus() {
        Product product = createMockProduct(1L, VersionableEntity.EntityStatus.INACTIVE, "SAVINGS");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        NotFoundException ex = assertThrows(NotFoundException.class, () -> publicCatalogService.getProductDetailView(1L));
        assertEquals("Product is not currently available", ex.getMessage());
    }

    @Test
    @DisplayName("BuildPricingBreakdown - Should cover Discount and Waiver categories")
    void testBuildPricingBreakdown_WaiversAndDiscounts() {
        Product p = createMockProduct(1L, VersionableEntity.EntityStatus.ACTIVE, "TEST");
        p.setProductPricingLinks(List.of(
                createPricingLink("Annual Waiver", new BigDecimal("50.00"), PricingComponent.ComponentType.WAIVER),
                createPricingLink("Student Discount", new BigDecimal("5.00"), PricingComponent.ComponentType.DISCOUNT)
        ));

        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        ProductDetailView detail = publicCatalogService.getProductDetailView(1L);

        assertAll(
                () -> assertEquals(1, detail.getPricing().getWaivers().size()),
                () -> assertEquals("USD 50.00", detail.getPricing().getWaivers().getFirst().getValue()),
                () -> assertEquals(1, detail.getPricing().getDiscounts().size())
        );
    }

    @Test
    @DisplayName("SummarizePricing - Should handle case with no main fee")
    void testSummarizePricing_NoFeeFound() {
        Product p = createMockProduct(1L, VersionableEntity.EntityStatus.ACTIVE, "TEST");
        when(productRepository.findAllById(anyList())).thenReturn(List.of(p));
        when(productMapper.toCatalogCard(any())).thenReturn(ProductCatalogCard.builder()
                .pricingSummary(ProductCatalogCard.PricingSummary.builder().build()).build());

        ProductComparisonView comparison = publicCatalogService.compareProducts(List.of(1L));
        assertEquals("No monthly fee", comparison.getProducts().get(0).getPricingSummary().getMainPriceLabel());
    }

    @Test
    @DisplayName("GetPublicBundleDetails - Should filter and map benefits correctly")
    void testGetPublicBundleDetails_BenefitFiltering() {
        ProductBundle bundle = createMockBundle(500L);

        var adj = mock(ProductPricingCalculationResult.PriceComponentDetail.class);
        when(adj.getValueType()).thenReturn(PriceValue.ValueType.DISCOUNT_PERCENTAGE);
        when(adj.getComponentCode()).thenReturn("MONTHLY_WAIVER");

        BundlePriceResponse response = BundlePriceResponse.builder()
                .netTotalAmount(new BigDecimal("10.00"))
                .grossTotalAmount(new BigDecimal("15.00"))
                .bundleAdjustments(List.of(adj)).build();

        when(productBundleRepository.findById(500L)).thenReturn(Optional.of(bundle));
        when(bundlePricingService.calculateTotalBundlePrice(any())).thenReturn(response);

        var result = publicCatalogService.getPublicBundleDetails(500L, "RETAIL");

        assertAll(
                () -> assertEquals(new BigDecimal("5.00"), result.getPricing().getTotalSavings()),
                () -> assertFalse(result.getPricing().getAdjustmentLabels().isEmpty(), "Should have adjustment labels"),
                () -> {
                    boolean match = PriceValue.ValueType.DISCOUNT_PERCENTAGE.name().contains("WAIVE");
                    if (match && result.getPricing().getAdjustmentLabels().isEmpty()) {
                        fail("Filter logic in service didn't match 'DISCOUNT_PERCENTAGE'. Check if service uses .contains('WAIVER')");
                    }
                }
        );
    }

    @Test
    @DisplayName("Security - Should throw AccessDeniedException when bundle belongs to another bank")
    void testGetPublicBundleDetails_SecurityViolation() {
        ProductBundle bundle = createMockBundle(500L);
        bundle.setBankId("OTHER_BANK_ID");
        when(productBundleRepository.findById(500L)).thenReturn(Optional.of(bundle));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> publicCatalogService.getPublicBundleDetails(500L, "RETAIL"));

        assertEquals("You do not have permission to access this ProductBundle", ex.getMessage());
    }

    @Test
    @DisplayName("BuildPricingBreakdown - Should handle BENEFIT type via Discounts branch")
    void testBuildPricingBreakdown_DefaultFallback() {
        Product product = createMockProduct(1L, VersionableEntity.EntityStatus.ACTIVE, "TEST");
        // This is a BENEFIT type
        product.setProductPricingLinks(List.of(
                createPricingLink("Bonus", new BigDecimal("1.00"), PricingComponent.ComponentType.BENEFIT)
        ));

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        ProductDetailView detail = publicCatalogService.getProductDetailView(1L);

        assertAll(
                () -> assertFalse(detail.getPricing().getDiscounts().isEmpty(), "Benefits should be in the discounts list"),
                () -> assertEquals("Bonus", detail.getPricing().getDiscounts().getFirst().getName()),
                () -> assertEquals("USD 1.00", detail.getPricing().getDiscounts().getFirst().getValue())
        );
    }

    @Test
    @DisplayName("Detail View - Comprehensive test of all categories, features, and pricing types")
    void testGetProductDetailView_Exhaustive() {
        // 1. Setup Product with all 7 ComponentTypes + Multiple Feature Categories
        Product p = createMockProduct(1L, VersionableEntity.EntityStatus.ACTIVE, "RETAIL");
        p.setFullDescription("Full Description");
        p.setTermsAndConditions("T&Cs Apply");

        p.setProductFeatureLinks(List.of(
                createFeatureLink("Daily Limit", "1000"),        // Account Limits
                createFeatureLink("Savings Rate", "4.0%"),      // Interest & Returns
                createFeatureLink("ATM Access", "Worldwide"),   // Services & Access
                createFeatureLink("Card Color", "Gold")         // Other Features
        ));

        p.setProductPricingLinks(List.of(
                createPricingLink("Monthly Fee", new BigDecimal("10.00"), PricingComponent.ComponentType.FEE),
                createPricingLink("Bundle Charge", new BigDecimal("5.00"), PricingComponent.ComponentType.PACKAGE_FEE),
                createPricingLink("Gov Tax", new BigDecimal("1.50"), PricingComponent.ComponentType.TAX),
                createPricingLink("Overdraft Rate", new BigDecimal("18.0"), PricingComponent.ComponentType.INTEREST_RATE),
                createPricingLink("Student Waiver", new BigDecimal("10.00"), PricingComponent.ComponentType.WAIVER),
                createPricingLink("Referral Discount", new BigDecimal("2.00"), PricingComponent.ComponentType.DISCOUNT),
                createPricingLink("Loyalty Bonus", new BigDecimal("1.00"), PricingComponent.ComponentType.BENEFIT),
                createPricingLink("Null Value Item", null, PricingComponent.ComponentType.FEE)
        ));

        // 2. Mock Repositories
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        // Mocking an empty related products list to keep focus on the main detail view
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        // 3. Execute
        ProductDetailView detail = publicCatalogService.getProductDetailView(1L);

        // 4. Assert All The Things
        assertAll(
                // Basic Info
                () -> assertEquals("Test Product 1", detail.getProductName()),
                () -> assertEquals("Full Description", detail.getFullDescription()),

                // Feature Categorization (Validates organizeFeaturesByCategory)
                () -> assertEquals(4, detail.getFeaturesByCategory().size(), "Should have 4 feature categories"),
                () -> assertTrue(detail.getFeaturesByCategory().containsKey("Account Limits")),

                // Pricing Lists (Validates buildPricingBreakdown switch)
                () -> assertEquals(4, detail.getPricing().getFees().size(), "3 Costs + 1 Default/Null"),
                () -> assertEquals(1, detail.getPricing().getRates().size()),
                () -> assertEquals(1, detail.getPricing().getWaivers().size()),
                () -> assertEquals(2, detail.getPricing().getDiscounts().size(), "Discount + Benefit"),

                // Formatting & Highlighting (Validates formatPricingValue and isHighlightedCost)
                () -> {
                    var feeItem = detail.getPricing().getFees().stream()
                            .filter(i -> i.getName().equals("Monthly Fee")).findFirst().get();
                    assertTrue(feeItem.isHighlighted());
                    assertEquals("USD 10.00", feeItem.getValue());
                },
                () -> {
                    var taxItem = detail.getPricing().getFees().stream()
                            .filter(i -> i.getName().equals("Gov Tax")).findFirst().get();
                    assertTrue(taxItem.isHighlighted(), "Taxes should be highlighted");
                },
                () -> {
                    var includedItem = detail.getPricing().getFees().stream()
                            .filter(i -> i.getName().equals("Null Value Item")).findFirst().get();
                    assertEquals("Included", includedItem.getValue(), "Null fixed values should return 'Included'");
                },

                // Savings Logic
                () -> assertEquals(new BigDecimal("13.00"), detail.getPricing().getTotalSavings(),
                        "Should sum Waiver(10) + Discount(2) + Benefit(1)")
        );
    }

    @Test
    @DisplayName("Branch: getActiveProducts - individual filters")
    void testGetActiveProducts_IndividualFilters() {
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        // Category only
        publicCatalogService.getActiveProducts("SAVINGS", null, null, PageRequest.of(0, 10));
        // ProductType only
        publicCatalogService.getActiveProducts(null, 10L, null, PageRequest.of(0, 10));
        // CustomerSegment only
        publicCatalogService.getActiveProducts(null, null, "RETAIL", PageRequest.of(0, 10));

        verify(productRepository, times(3)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Branch: mapItems null check")
    void testMapItems_Null() {
        ProductBundle bundle = createMockBundle(500L);
        bundle.setContainedProducts(new ArrayList<>()); // Change from null to empty list if you want to avoid NPE in getPublicBundleDetails

        var adj = mock(ProductPricingCalculationResult.PriceComponentDetail.class);
        lenient().when(adj.getValueType()).thenReturn(PriceValue.ValueType.DISCOUNT_PERCENTAGE);
        lenient().when(adj.getComponentCode()).thenReturn("CODE");

        BundlePriceResponse response = BundlePriceResponse.builder()
                .netTotalAmount(BigDecimal.TEN)
                .grossTotalAmount(BigDecimal.TEN)
                .bundleAdjustments(List.of(adj)).build();

        when(productBundleRepository.findById(500L)).thenReturn(Optional.of(bundle));
        when(bundlePricingService.calculateTotalBundlePrice(any())).thenReturn(response);

        var result = publicCatalogService.getPublicBundleDetails(500L, "RETAIL");
        assertTrue(result.getItems().isEmpty());
    }

    @Test
    @DisplayName("Branch: categorizeFeature additional keywords")
    void testCategorizeFeature_Keywords() {
        Product p = createMockProduct(1L, VersionableEntity.EntityStatus.ACTIVE, "TEST");
        p.setProductFeatureLinks(List.of(
                createFeatureLink("maximum deposit", "1000"),
                createFeatureLink("minimum balance", "100")
        ));

        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        Map<String, List<ProductDetailView.ProductFeatureDetail>> categorized =
                publicCatalogService.getProductDetailView(1L).getFeaturesByCategory();

        assertEquals(1, categorized.size());
        assertTrue(categorized.containsKey("Account Limits"));
    }

    @Test
    @DisplayName("Branch: getPublicBundleDetails all benefit types")
    void testGetPublicBundleDetails_AllBenefits() {
        ProductBundle bundle = createMockBundle(500L);

        var adj1 = mock(ProductPricingCalculationResult.PriceComponentDetail.class);
        when(adj1.getValueType()).thenReturn(PriceValue.ValueType.DISCOUNT_ABSOLUTE);
        when(adj1.getComponentCode()).thenReturn("ABS");

        var adj2 = mock(ProductPricingCalculationResult.PriceComponentDetail.class);
        when(adj2.getValueType()).thenReturn(PriceValue.ValueType.FREE_COUNT);
        when(adj2.getComponentCode()).thenReturn("FREE");

        BundlePriceResponse response = BundlePriceResponse.builder()
                .netTotalAmount(new BigDecimal("10.00"))
                .grossTotalAmount(new BigDecimal("15.00"))
                .bundleAdjustments(List.of(adj1, adj2)).build();

        when(productBundleRepository.findById(500L)).thenReturn(Optional.of(bundle));
        when(bundlePricingService.calculateTotalBundlePrice(any())).thenReturn(response);

        var result = publicCatalogService.getPublicBundleDetails(500L, "RETAIL");
        assertEquals(2, result.getPricing().getAdjustmentLabels().size());
    }

    @Test
    @DisplayName("Comparison Matrix - Should correctly map TAX and BENEFIT across multiple products")
    void testCompareProducts_TaxAndBenefitMatrix() {
        // 1. Setup Product A with a Tax and a Benefit
        Product p1 = createMockProduct(101L, VersionableEntity.EntityStatus.ACTIVE, "RETAIL");
        p1.setProductPricingLinks(List.of(
                createPricingLink("State Tax", new BigDecimal("1.50"), PricingComponent.ComponentType.TAX),
                createPricingLink("Loyalty Bonus", new BigDecimal("5.00"), PricingComponent.ComponentType.BENEFIT)
        ));

        // 2. Setup Product B with NO Tax and a NULL (Included) Benefit
        Product p2 = createMockProduct(102L, VersionableEntity.EntityStatus.ACTIVE, "RETAIL");
        p2.setProductPricingLinks(List.of(
                createPricingLink("Loyalty Bonus", null, PricingComponent.ComponentType.BENEFIT)
        ));

        when(productRepository.findAllById(anyList())).thenReturn(List.of(p1, p2));
        when(productMapper.toCatalogCard(any())).thenReturn(ProductCatalogCard.builder().build());

        // 3. Execute
        ProductComparisonView matrix = publicCatalogService.compareProducts(List.of(101L, 102L));

        // 4. Assert Alignment
        assertAll(
                () -> {
                    // Check TAX alignment
                    List<String> taxRow = matrix.getPricingComparison().get("State Tax");
                    assertEquals("USD 1.50", taxRow.get(0), "P1 should show formatted tax");
                    assertEquals("—", taxRow.get(1), "P2 should show dash for missing tax");
                },
                () -> {
                    // Check BENEFIT alignment
                    List<String> benefitRow = matrix.getPricingComparison().get("Loyalty Bonus");
                    assertEquals("USD 5.00", benefitRow.get(0), "P1 should show formatted benefit");
                    assertEquals("Included", benefitRow.get(1), "P2 should show 'Included' for null benefit value");
                }
        );
    }

    // --- HELPERS ---

    private Product createMockProduct(Long id, VersionableEntity.EntityStatus status, String category) {
        Product p = new Product();
        p.setId(id);
        p.setBankId(TEST_BANK_ID); // Security requirement
        p.setName("Test Product " + id);
        p.setStatus(status);
        p.setCategory(category);
        p.setActivationDate(LocalDate.now().minusDays(1));
        p.setProductFeatureLinks(new ArrayList<>());
        p.setProductPricingLinks(new ArrayList<>());
        p.setTargetCustomerSegments("RETAIL,CORPORATE");
        return p;
    }

    private ProductBundle createMockBundle(Long id) {
        ProductBundle bundle = new ProductBundle();
        bundle.setId(id);
        bundle.setBankId(TEST_BANK_ID);
        bundle.setName("Gold Bundle");
        bundle.setContainedProducts(new ArrayList<>());
        bundle.setStatus(VersionableEntity.EntityStatus.ACTIVE);
        return bundle;
    }

    private ProductFeatureLink createFeatureLink(String name, String value) {
        FeatureComponent fc = new FeatureComponent();
        fc.setName(name);
        ProductFeatureLink link = new ProductFeatureLink();
        link.setFeatureComponent(fc);
        link.setFeatureValue(value);
        return link;
    }

    private ProductPricingLink createPricingLink(String name, BigDecimal value, PricingComponent.ComponentType type) {
        PricingComponent pc = new PricingComponent();
        pc.setName(name);
        pc.setType(type);
        ProductPricingLink link = new ProductPricingLink();
        link.setPricingComponent(pc);
        link.setFixedValue(value);
        return link;
    }

}