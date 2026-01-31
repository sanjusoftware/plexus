package com.bankengine.pricing.service;

import com.bankengine.catalog.model.BundleProductLink;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.repository.ProductBundleRepository;
import com.bankengine.common.exception.ValidationException;
import com.bankengine.pricing.dto.BundlePriceRequest;
import com.bankengine.pricing.dto.BundlePriceResponse;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.model.BundlePricingLink;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.repository.BundlePricingLinkRepository;
import com.bankengine.rules.model.BundlePricingInput;
import com.bankengine.rules.service.BundleRulesEngineService;
import com.bankengine.test.config.BaseServiceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BundlePricingServiceTest extends BaseServiceTest {

    @Mock
    private PricingCalculationService pricingCalculationService;
    @Mock
    private BundleRulesEngineService bundleRulesEngineService;
    @Mock
    private ProductBundleRepository productBundleRepository;
    @Mock
    private BundlePricingLinkRepository bundlePricingLinkRepository;

    @InjectMocks
    private BundlePricingService bundlePricingService;

    @Test
    @DisplayName("Branch: Should throw ValidationException when product list is empty")
    void calculateTotalBundlePrice_EmptyProducts_ThrowsException() {
        BundlePriceRequest request = BundlePriceRequest.builder()
                .products(List.of())
                .build();

        assertThrows(ValidationException.class, () -> bundlePricingService.calculateTotalBundlePrice(request));
    }

    @Test
    @DisplayName("Exhaustive: Should calculate Mixed Adjustments (Fixed ABS, Rule PCT, Waived, FreeCount)")
    void calculateTotalBundlePrice_ExhaustiveLogic() {
        // 1. Setup Request: Two products totaling $200
        Long bundleId = 500L;
        var pReq1 = new BundlePriceRequest.ProductRequest(10L, BigDecimal.ZERO);
        var pReq2 = new BundlePriceRequest.ProductRequest(11L, BigDecimal.ZERO);

        BundlePriceRequest request = BundlePriceRequest.builder()
                .productBundleId(bundleId)
                .products(List.of(pReq1, pReq2))
                .customerSegment("GOLD")
                .build();

        // Mock individual products returning $100 each
        when(pricingCalculationService.getProductPricing(any())).thenReturn(
                ProductPricingCalculationResult.builder()
                        .finalChargeablePrice(new BigDecimal("100.00"))
                        .build()
        );

        // 2. Mock Bundle with 1 Fixed Fee ($5 Absolute) and 1 Rule Component
        PricingComponent feeComp = new PricingComponent();
        feeComp.setName("BUNDLE_ADMIN_FEE");

        BundlePricingLink fixedFeeLink = new BundlePricingLink();
        fixedFeeLink.setPricingComponent(feeComp);
        fixedFeeLink.setFixedValue(new BigDecimal("5.00"));
        fixedFeeLink.setFixedValueType(PriceValue.ValueType.FEE_ABSOLUTE);
        fixedFeeLink.setUseRulesEngine(false);

        PricingComponent ruleComp = new PricingComponent();
        ruleComp.setId(99L);
        ruleComp.setName("DYNAMIC_PROMO");

        BundlePricingLink rulesLink = new BundlePricingLink();
        rulesLink.setPricingComponent(ruleComp);
        rulesLink.setUseRulesEngine(true);

        // STUB REPOSITORY: Tell the service these links are "active" in the DB
        List<BundlePricingLink> activeLinks = List.of(fixedFeeLink, rulesLink);
        when(bundlePricingLinkRepository.findByBundleIdAndDate(any(), any()))
                .thenReturn(activeLinks);

        ProductBundle bundle = new ProductBundle();
        bundle.setId(bundleId);
        bundle.setBundlePricingLinks(activeLinks);

        // Setup internal products for Drools context
        Product p1 = new Product(); p1.setId(10L);
        Product p2 = new Product(); p2.setId(11L);
        BundleProductLink bpl1 = new BundleProductLink(); bpl1.setProduct(p1);
        BundleProductLink bpl2 = new BundleProductLink(); bpl2.setProduct(p2);
        bundle.setContainedProducts(List.of(bpl1, bpl2));

        when(productBundleRepository.findById(bundleId)).thenReturn(Optional.of(bundle));

        // 3. Mock Rules Engine: Adding a 10% Discount and a WAIVED component
        BundlePricingInput rulesOutput = new BundlePricingInput();
        rulesOutput.addAdjustment("BUNDLE_DISCOUNT", new BigDecimal("10.00"), "DISCOUNT_PERCENTAGE");
        rulesOutput.addAdjustment("OVERDRAFT_PROTECTION", BigDecimal.ZERO, "WAIVED");

        when(bundleRulesEngineService.determineBundleAdjustments(any())).thenReturn(rulesOutput);

        // 4. Act
        BundlePriceResponse response = bundlePricingService.calculateTotalBundlePrice(request);

        // 5. Assert Math and Logic Branches
        // Base Products = 200
        // Fees = 5 (Fixed)
        // Gross = 205
        // Discounts = 10% of 200 (base is products only) = -20
        // Net = 205 - 20 = 185
        assertAll(
                () -> assertEquals(new BigDecimal("205.00"), response.getGrossTotalAmount(), "Gross should include fixed fees"),
                () -> assertEquals(new BigDecimal("185.00"), response.getNetTotalAmount(), "Net should apply percentage discount"),
                () -> assertEquals(3, response.getBundleAdjustments().size(), "Should have Admin Fee, Discount, and Waived items"),

                // Verify percentage calculation logic
                () -> {
                    var discount = response.getBundleAdjustments().stream()
                            .filter(a -> a.getComponentCode().equals("BUNDLE_DISCOUNT"))
                            .findFirst().orElseThrow();
                    assertEquals(new BigDecimal("-20.00"), discount.getCalculatedAmount());
                },

                // Verify WAIVED logic (0 amount, but present)
                () -> assertTrue(response.getBundleAdjustments().stream()
                        .anyMatch(a -> a.getValueType() == PriceValue.ValueType.WAIVED), "Waived item should be present")
        );
    }

    @Test
    @DisplayName("Branch: Should handle FREE_COUNT and Absolute Discount")
    void calculateTotalBundlePrice_FreeCountAndAbsolute() {
        Long bundleId = 1L;
        BundlePriceRequest request = BundlePriceRequest.builder()
                .productBundleId(bundleId)
                .products(List.of(new BundlePriceRequest.ProductRequest(10L, BigDecimal.ZERO)))
                .build();

        when(pricingCalculationService.getProductPricing(any())).thenReturn(
                ProductPricingCalculationResult.builder().finalChargeablePrice(new BigDecimal("50.00")).build()
        );

        ProductBundle bundle = new ProductBundle();
        bundle.setBundlePricingLinks(new ArrayList<>());
        bundle.setContainedProducts(new ArrayList<>());
        when(productBundleRepository.findById(any())).thenReturn(Optional.of(bundle));

        // Return empty list for this test as we only want Rules Engine adjustments
        when(bundlePricingLinkRepository.findByBundleIdAndDate(any(), any()))
                .thenReturn(new ArrayList<>());

        BundlePricingInput rulesOutput = new BundlePricingInput();
        rulesOutput.addAdjustment("FREE_TRANSFERS", new BigDecimal("5.00"), "FREE_COUNT");
        rulesOutput.addAdjustment("LOYALTY_CREDIT", new BigDecimal("10.00"), "DISCOUNT_ABSOLUTE");

        when(bundleRulesEngineService.determineBundleAdjustments(any())).thenReturn(rulesOutput);

        var response = bundlePricingService.calculateTotalBundlePrice(request);

        // Net = 50 - 10 = 40. (FREE_COUNT doesn't impact net price math, just visibility)
        assertEquals(new BigDecimal("40.00"), response.getNetTotalAmount());
        assertTrue(response.getBundleAdjustments().stream()
                .anyMatch(a -> a.getValueType() == PriceValue.ValueType.FREE_COUNT));
    }
}