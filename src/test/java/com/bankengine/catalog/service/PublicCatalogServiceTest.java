package com.bankengine.catalog.service;

import com.bankengine.catalog.converter.ProductMapper;
import com.bankengine.catalog.dto.ProductCatalogCard;
import com.bankengine.catalog.dto.ProductComparisonView;
import com.bankengine.catalog.dto.ProductDetailView;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.pricing.dto.PricingRequest;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.ProductPricingLink;
import com.bankengine.pricing.service.PricingCalculationService;
import com.bankengine.test.config.BaseServiceTest;
import com.bankengine.web.exception.NotFoundException;
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

    @Mock private ProductRepository productRepository;
    @Mock private ProductMapper productMapper;
    @Mock private PricingCalculationService pricingCalculationService;

    @InjectMocks private PublicCatalogService publicCatalogService;

    @Test
    @DisplayName("Recommendations - Should personalize prices and sort by cheapest")
    void testGetRecommendedProducts() {
        Product p1 = createMockProduct(101L, "ACTIVE", "RETAIL");
        Product p2 = createMockProduct(102L, "ACTIVE", "RETAIL");

        when(productRepository.findAll(any(Specification.class))).thenReturn(List.of(p1, p2));

        when(productMapper.toCatalogCard(any())).thenAnswer(inv -> ProductCatalogCard.builder()
                .pricingSummary(ProductCatalogCard.PricingSummary.builder().build())
                .build());

        when(pricingCalculationService.getProductPricing(any())).thenAnswer(inv -> {
            var req = (PricingRequest) inv.getArgument(0);
            BigDecimal price = req.getProductId().equals(101L) ? new BigDecimal("20.00") : new BigDecimal("5.00");
            return ProductPricingCalculationResult.builder().finalChargeablePrice(price).build();
        });

        List<ProductCatalogCard> results = publicCatalogService.getRecommendedProducts("RETAIL", new BigDecimal("5000"));

        assertEquals(2, results.size());
        assertEquals(new BigDecimal("5.00"), results.get(0).getPricingSummary().getMainPriceValue(), "Cheapest product should be first");
    }

    @Test
    @DisplayName("Detail View - Should throw NotFoundException for non-existent product")
    void testGetProductDetailView_NotFound() {
        when(productRepository.findById(999L)).thenReturn(Optional.ofNullable(null));
        assertThrows(NotFoundException.class, () -> publicCatalogService.getProductDetailView(999L));
    }

    @Test
    @DisplayName("Feature Categorization - Should map keywords to correct display categories")
    void testOrganizeFeaturesByCategory_Keywords() {
        Product p = createMockProduct(1L, "ACTIVE", "TEST");
        p.setProductFeatureLinks(List.of(
                createFeatureLink("Daily Withdrawal Limit", "500"),   // Hits "Account Limits"
                createFeatureLink("Base Interest Rate", "2.5%"),      // Hits "Interest & Returns"
                createFeatureLink("Global ATM Service", "Free"),      // Hits "Services & Access"
                createFeatureLink("Color", "Blue")                    // Hits "Other Features"
        ));

        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        lenient().when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        Map<String, List<ProductDetailView.ProductFeatureDetail>> categorized =
                publicCatalogService.getProductDetailView(1L).getFeaturesByCategory();

        assertAll(
                () -> assertTrue(categorized.containsKey("Account Limits")),
                () -> assertTrue(categorized.containsKey("Interest & Returns")),
                () -> assertTrue(categorized.containsKey("Services & Access")),
                () -> assertTrue(categorized.containsKey("Other Features")),
                () -> assertEquals(1, categorized.get("Account Limits").size())
        );
    }

    @Test
    @DisplayName("Pricing Breakdown - Should format values based on ComponentType")
    void testBuildPricingBreakdown_Formatting() {
        Product p = createMockProduct(1L, "ACTIVE", "TEST");

        PricingComponent feeComp = new PricingComponent();
        feeComp.setName("Monthly Fee");
        feeComp.setType(PricingComponent.ComponentType.FEE);

        ProductPricingLink feeLink = new ProductPricingLink();
        feeLink.setPricingComponent(feeComp);
        feeLink.setFixedValue(new BigDecimal("10.00"));

        PricingComponent rateComp = new PricingComponent();
        rateComp.setName("Overdraft Rate");
        rateComp.setType(PricingComponent.ComponentType.INTEREST_RATE);

        ProductPricingLink rateLink = new ProductPricingLink();
        rateLink.setPricingComponent(rateComp);
        rateLink.setFixedValue(new BigDecimal("15.5"));

        p.setProductPricingLinks(List.of(feeLink, rateLink));

        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        lenient().when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        ProductDetailView.PricingBreakdown breakdown = publicCatalogService.getProductDetailView(1L).getPricing();

        assertAll(
                () -> assertEquals("$10.00", breakdown.getFees().get(0).getValue()),
                () -> assertEquals("15.5% p.a.", breakdown.getRates().get(0).getValue()),
                () -> assertTrue(breakdown.getFees().get(0).isHighlighted(), "Fees should be highlighted")
        );
    }

    @Test
    @DisplayName("Comparison Matrix - Should handle products with disjoint feature/pricing sets")
    void testCompareProducts_DisjointSets() {
        Product p1 = createMockProduct(101L, "ACTIVE", "TEST");
        Product p2 = createMockProduct(102L, "ACTIVE", "TEST");

        p1.setProductFeatureLinks(List.of(createFeatureLink("FeatureA", "ValA")));
        p2.setProductFeatureLinks(List.of(createFeatureLink("FeatureB", "ValB")));
        p1.setProductPricingLinks(List.of(createPricingLink("PriceX", new BigDecimal("1.00"))));
        p2.setProductPricingLinks(List.of(createPricingLink("PriceY", null)));

        when(productRepository.findAllById(anyList())).thenReturn(List.of(p1, p2));
        when(productMapper.toCatalogCard(any())).thenAnswer(inv -> ProductCatalogCard.builder().build());

        ProductComparisonView matrix = publicCatalogService.compareProducts(List.of(101L, 102L));

        assertAll(
                () -> assertEquals("ValA", matrix.getFeatureComparison().get("FeatureA").get(0)),
                () -> assertEquals("—", matrix.getFeatureComparison().get("FeatureA").get(1)),
                () -> assertEquals("$1.00", matrix.getPricingComparison().get("PriceX").get(0)),
                () -> assertEquals("Included", matrix.getPricingComparison().get("PriceY").get(1))
        );
    }

    @Test
    @DisplayName("Recommendations - Should provide fallback message when pricing engine fails")
    void testGetRecommendedProducts_PricingFailure() {
        Product p = createMockProduct(1L, "ACTIVE", "RETAIL");

        when(productRepository.findAll(any(Specification.class))).thenReturn(List.of(p));
        when(productMapper.toCatalogCard(any())).thenReturn(ProductCatalogCard.builder()
                .pricingSummary(ProductCatalogCard.PricingSummary.builder().build())
                .build());

        when(pricingCalculationService.getProductPricing(any())).thenThrow(new RuntimeException("Service Down"));
        List<ProductCatalogCard> results = publicCatalogService.getRecommendedProducts("RETAIL", new BigDecimal("1000"));
        assertEquals("Pricing currently unavailable", results.get(0).getEligibilityMessage());
    }

    @Test
    @DisplayName("Filtering - Should apply category, type and segment filters correctly")
    void testGetActiveProducts_FullFiltering() {
        Page<Product> mockPage = new PageImpl<>(List.of(createMockProduct(1L, "ACTIVE", "SAVINGS")));
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(mockPage);
        when(productMapper.toCatalogCard(any())).thenReturn(ProductCatalogCard.builder().build());

        // Calling with all parameters triggers all Specification lambda branches in getActiveProducts
        Page<ProductCatalogCard> result = publicCatalogService.getActiveProducts("SAVINGS", 10L, "RETAIL", PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(productRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Detail View - Should trigger related products search and mapping")
    void testGetProductDetailView_RelatedProducts() {
        Product current = createMockProduct(1L, "ACTIVE", "RETAIL");
        Product related = createMockProduct(2L, "ACTIVE", "RETAIL");

        when(productRepository.findById(1L)).thenReturn(Optional.of(current));

        // This exercises the findRelatedProducts logic by returning a content-rich Page
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(related)));
        when(productMapper.toCatalogCard(any())).thenReturn(ProductCatalogCard.builder().build());

        ProductDetailView detail = publicCatalogService.getProductDetailView(1L);

        assertFalse(detail.getRelatedProducts().isEmpty());
        assertEquals(1, detail.getRelatedProducts().size());
    }

    @Test
    @DisplayName("Pricing Breakdown - Should handle Tiered and Switch fallbacks")
    void testBuildPricingBreakdown_EdgeCases() {
        Product p = createMockProduct(1L, "ACTIVE", "TEST");

        PricingComponent tieredComp = new PricingComponent();
        tieredComp.setName("Tiered Rate");
        tieredComp.setType(PricingComponent.ComponentType.FEE);

        ProductPricingLink link = new ProductPricingLink();
        link.setPricingComponent(tieredComp);
        link.setFixedValue(new BigDecimal("5.00"));
        p.setProductPricingLinks(List.of(link));

        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        lenient().when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        ProductDetailView.PricingBreakdown breakdown = publicCatalogService.getProductDetailView(1L).getPricing();

        // Components typed as FEE or unknown default to the Fees list in the service switch
        assertFalse(breakdown.getFees().isEmpty());
        assertEquals("$5.00", breakdown.getFees().get(0).getValue());
    }

    private ProductFeatureLink createFeatureLink(String name, String value) {
        FeatureComponent fc = new FeatureComponent();
        fc.setName(name);
        ProductFeatureLink link = new ProductFeatureLink();
        link.setFeatureComponent(fc);
        link.setFeatureValue(value);
        return link;
    }

    private ProductPricingLink createPricingLink(String name, BigDecimal value) {
        PricingComponent pc = new PricingComponent();
        pc.setName(name);
        pc.setType(PricingComponent.ComponentType.FEE);
        ProductPricingLink link = new ProductPricingLink();
        link.setPricingComponent(pc);
        link.setFixedValue(value);
        return link;
    }

    private Product createMockProduct(Long id, String status, String category) {
        Product p = new Product();
        p.setId(id);
        p.setName("Test Product " + id);
        p.setStatus(status);
        p.setCategory(category);
        p.setEffectiveDate(LocalDate.now().minusDays(1));
        p.setProductFeatureLinks(new ArrayList<>());
        p.setProductPricingLinks(new ArrayList<>());
        p.setTargetCustomerSegments("RETAIL,CORPORATE");
        return p;
    }
}