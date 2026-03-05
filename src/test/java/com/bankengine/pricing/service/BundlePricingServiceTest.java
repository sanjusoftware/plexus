package com.bankengine.pricing.service;

import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.repository.ProductBundleRepository;
import com.bankengine.pricing.dto.BundlePriceRequest;
import com.bankengine.pricing.dto.BundlePriceResponse;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.model.BundlePricingLink;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.repository.BundlePricingLinkRepository;
import com.bankengine.rules.model.BundlePricingInput;
import com.bankengine.rules.service.BundleRulesEngineService;
import com.bankengine.test.config.BaseServiceTest;
import com.bankengine.web.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BundlePricingServiceTest extends BaseServiceTest {

    @Mock private ProductPricingService productPricingService;
    @Mock private BundleRulesEngineService bundleRulesEngineService;
    @Mock private ProductBundleRepository productBundleRepository;
    @Mock private BundlePricingLinkRepository bundlePricingLinkRepository;

    @Spy private PriceAggregator priceAggregator = new PriceAggregator();
    @InjectMocks private BundlePricingService bundlePricingService;

    // -----------------------------------------------------------------------------------
    // VALIDATION TESTS
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("Branch: Should throw ValidationException when product list is empty")
    void calculateTotalBundlePrice_EmptyProducts_ThrowsException() {
        BundlePriceRequest request = BundlePriceRequest.builder().products(List.of()).build();
        assertThrows(ValidationException.class, () -> bundlePricingService.calculateTotalBundlePrice(request));
    }

    // -----------------------------------------------------------------------------------
    // CORE LOGIC TESTS (EXHAUSTIVE)
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("Exhaustive: Should calculate $184.50 (10% Discount on Gross Pool)")
    void calculateTotalBundlePrice_ExhaustiveLogic() {
        // 1. Setup Request: Two products totaling $200
        Long bundleId = 500L;
        BundlePriceRequest request = BundlePriceRequest.builder()
                .productBundleId(bundleId)
                .products(List.of(
                    BundlePriceRequest.BundleProductItem.builder().productId(10L).transactionAmount(BigDecimal.ZERO).build(),
                    BundlePriceRequest.BundleProductItem.builder().productId(11L).transactionAmount(BigDecimal.ZERO).build()
                ))
                .customerSegment("GOLD")
                .build();

        // Each product returns $100.00
        when(productPricingService.getProductPricing(any())).thenReturn(
                ProductPricingCalculationResult.builder().finalChargeablePrice(new BigDecimal("100.00")).build()
        );

        // 2. Setup Bundle Links ($5.00 Fee)
        BundlePricingLink fixedFeeLink = BundlePricingLink.builder()
                .pricingComponent(PricingComponent.builder().name("BUNDLE_ADMIN_FEE").build())
                .fixedValue(new BigDecimal("5.00"))
                .fixedValueType(PriceValue.ValueType.FEE_ABSOLUTE)
                .useRulesEngine(false)
                .build();

        when(bundlePricingLinkRepository.findByBundleIdAndDate(any(), any())).thenReturn(List.of(fixedFeeLink));
        when(productBundleRepository.findById(bundleId)).thenReturn(Optional.of(ProductBundle.builder().id(bundleId).build()));

        // 3. Mock Rules: 10% Global Discount (No Target)
        // Math: Product($200) + Fee($5) = $205 Gross.
        // Discount: 10% of $205 = $20.50
        BundlePricingInput rulesOutput = new BundlePricingInput();
        rulesOutput.addAdjustment("BUNDLE_DISCOUNT", new BigDecimal("10.00"), "DISCOUNT_PERCENTAGE");
        when(bundleRulesEngineService.determineBundleAdjustments(any())).thenReturn(rulesOutput);

        // 4. Act
        BundlePriceResponse response = bundlePricingService.calculateTotalBundlePrice(request);

        // 5. Assert: Verify the Global Pool Logic
        assertAll(
                () -> assertScaledBigDecimal("205.00", response.getGrossTotalAmount(), "Gross = 200 products + 5 bundle fee"),
                () -> assertScaledBigDecimal("184.50", response.getNetTotalAmount(), "Net = 205 - 20.50 discount"),

                () -> {
                    var discount = response.getBundleAdjustments().stream()
                            .filter(a -> a.getComponentCode().equals("BUNDLE_DISCOUNT")).findFirst().orElseThrow();
                    assertScaledBigDecimal("-20.50", discount.getCalculatedAmount(), "Discount applies to the $205.00 total pool");
                }
        );
    }

    // -----------------------------------------------------------------------------------
    // SCENARIO TESTS
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("Scenario: Targeted Discount should only apply to the specific target fee")
    void calculateTotalBundlePrice_TargetedDiscountLogic() {
        BundlePriceRequest request = BundlePriceRequest.builder()
                .productBundleId(1L)
                .products(List.of(BundlePriceRequest.BundleProductItem.builder().productId(10L).transactionAmount(BigDecimal.ZERO).build()))
                .build();

        // Product Base Fee is $1000
        when(productPricingService.getProductPricing(any())).thenReturn(
                ProductPricingCalculationResult.builder().finalChargeablePrice(new BigDecimal("1000.00")).build());

        // Setup two different Bundle Fees: Service Fee($100) and Tech Fee($50)
        BundlePricingLink feeALink = createLink("SERVICE_FEE", "100.00");
        BundlePricingLink feeBLink = createLink("TECH_FEE", "50.00");

        when(bundlePricingLinkRepository.findByBundleIdAndDate(any(), any())).thenReturn(List.of(feeALink, feeBLink));
        when(productBundleRepository.findById(any())).thenReturn(Optional.of(ProductBundle.builder().build()));

        // 50% discount targeting TECH_FEE only
        BundlePricingInput rulesOutput = new BundlePricingInput();
        rulesOutput.addAdjustment("TECH_DISCOUNT", new BigDecimal("50.00"), "DISCOUNT_PERCENTAGE", "TECH_FEE", false);
        when(bundleRulesEngineService.determineBundleAdjustments(any())).thenReturn(rulesOutput);

        var response = bundlePricingService.calculateTotalBundlePrice(request);

        // Math: 1000 + 100 + 50 = 1150 Gross.
        // Discount: 50% of 50 = 25.00
        // Net: 1150 - 25 = 1125.00
        assertAll(
                () -> assertScaledBigDecimal("1150.00", response.getGrossTotalAmount()),
                () -> assertScaledBigDecimal("1125.00", response.getNetTotalAmount()),
                () -> {
                    var discount = response.getBundleAdjustments().stream()
                            .filter(a -> a.getComponentCode().equals("TECH_DISCOUNT")).findFirst().orElseThrow();
                    assertScaledBigDecimal("-25.00", discount.getCalculatedAmount());
                }
        );
    }

    @Test
    @DisplayName("Scenario: FREE_COUNT should have 0.00 monetary impact")
    void calculateTotalBundlePrice_FreeCountAssertion() {
        BundlePriceRequest request = BundlePriceRequest.builder()
                .productBundleId(1L)
                .products(List.of(BundlePriceRequest.BundleProductItem.builder().productId(10L).transactionAmount(BigDecimal.ZERO).build()))
                .build();

        when(productPricingService.getProductPricing(any())).thenReturn(
                ProductPricingCalculationResult.builder().finalChargeablePrice(new BigDecimal("50.00")).build());
        when(productBundleRepository.findById(any())).thenReturn(Optional.of(new ProductBundle()));
        when(bundlePricingLinkRepository.findByBundleIdAndDate(any(), any())).thenReturn(new ArrayList<>());

        BundlePricingInput rulesOutput = new BundlePricingInput();
        rulesOutput.addAdjustment("FREE_TRANSFERS", new BigDecimal("5.00"), "FREE_COUNT");
        when(bundleRulesEngineService.determineBundleAdjustments(any())).thenReturn(rulesOutput);

        var response = bundlePricingService.calculateTotalBundlePrice(request);

        var freeCount = response.getBundleAdjustments().stream().findFirst().orElseThrow();
        assertScaledBigDecimal("0.00", freeCount.getCalculatedAmount(), "Monetary impact of free count must be zero");
    }

    @Test
    @DisplayName("Scenario: Pro-Rata applies to bundle fees (50% for mid-month)")
    void calculateTotalBundlePrice_ApplyProRata() {
        LocalDate midMonth = LocalDate.of(2024, 6, 16); // 15 days left in 30-day month
        BundlePriceRequest request = BundlePriceRequest.builder()
                .productBundleId(1L)
                .enrollmentDate(midMonth)
                .effectiveDate(midMonth)
                .products(List.of(BundlePriceRequest.BundleProductItem.builder().productId(10L).transactionAmount(BigDecimal.ZERO).build()))
                .build();

        when(productPricingService.getProductPricing(any())).thenReturn(
                ProductPricingCalculationResult.builder().finalChargeablePrice(new BigDecimal("100.00")).build());

        PricingTier tier = PricingTier.builder().proRataApplicable(true).build();
        PricingComponent component = PricingComponent.builder()
                .name("SERVICE_FEE")
                .pricingTiers(List.of(tier))
                .build();

        BundlePricingLink link = BundlePricingLink.builder()
                .pricingComponent(component)
                .fixedValue(new BigDecimal("20.00"))
                .fixedValueType(PriceValue.ValueType.FEE_ABSOLUTE)
                .build();

        when(bundlePricingLinkRepository.findByBundleIdAndDate(any(), any())).thenReturn(List.of(link));
        when(productBundleRepository.findById(any())).thenReturn(Optional.of(new ProductBundle()));
        when(bundleRulesEngineService.determineBundleAdjustments(any())).thenReturn(new BundlePricingInput());

        var response = bundlePricingService.calculateTotalBundlePrice(request);

        // $100 product fee + ($20 fee pro-rated by 50% = $10) = $110.00
        assertScaledBigDecimal("110.00", response.getNetTotalAmount());
        assertScaledBigDecimal("10.00", response.getBundleAdjustments().getFirst().getCalculatedAmount());
    }

    @Test
    @DisplayName("Scenario: Full Breach Charge logic applied to bundle tier")
    void calculateTotalBundlePrice_FullBreachLogic() {
        BundlePriceRequest request = BundlePriceRequest.builder()
                .productBundleId(1L)
                .products(List.of(
                    BundlePriceRequest.BundleProductItem.builder()
                        .productId(10L)
                        .transactionAmount(new BigDecimal("5000.00")) // Breach amount
                        .build()
                ))
                .build();

        when(productPricingService.getProductPricing(any())).thenReturn(
                ProductPricingCalculationResult.builder().finalChargeablePrice(BigDecimal.ZERO).build());

        PricingTier breachTier = PricingTier.builder()
                .applyChargeOnFullBreach(true)
                .maxThreshold(new BigDecimal("1000.00"))
                .build();

        BundlePricingLink link = BundlePricingLink.builder()
                .pricingComponent(PricingComponent.builder().name("OVER_LIMIT_FEE").pricingTiers(List.of(breachTier)).build())
                .fixedValue(new BigDecimal("50.00"))
                .fixedValueType(PriceValue.ValueType.FEE_ABSOLUTE)
                .build();

        when(bundlePricingLinkRepository.findByBundleIdAndDate(any(), any())).thenReturn(List.of(link));
        when(productBundleRepository.findById(any())).thenReturn(Optional.of(new ProductBundle()));
        when(bundleRulesEngineService.determineBundleAdjustments(any())).thenReturn(new BundlePricingInput());

        var response = bundlePricingService.calculateTotalBundlePrice(request);

        // Verify that the detail carrying the flag is present in the response
        ProductPricingCalculationResult.PriceComponentDetail detail = response.getBundleAdjustments().getFirst();
        assertTrue(detail.isApplyChargeOnFullBreach(), "The response detail should flag that full breach logic was applied");
        assertScaledBigDecimal("50.00", detail.getCalculatedAmount());
    }

    @Test
    @DisplayName("Branch: Handle null transaction amounts in bundle calculation")
    void calculateTotalBundlePrice_NullTransactionAmount() {
        BundlePriceRequest request = BundlePriceRequest.builder()
                .productBundleId(1L)
                .products(List.of(
                    BundlePriceRequest.BundleProductItem.builder()
                        .productId(10L)
                        .transactionAmount(null)
                        .build()
                ))
                .build();

        when(productPricingService.getProductPricing(any())).thenReturn(
                ProductPricingCalculationResult.builder().finalChargeablePrice(new BigDecimal("50.00")).build());
        when(productBundleRepository.findById(any())).thenReturn(Optional.of(new ProductBundle()));
        when(bundlePricingLinkRepository.findByBundleIdAndDate(any(), any())).thenReturn(new ArrayList<>());
        when(bundleRulesEngineService.determineBundleAdjustments(any())).thenReturn(new BundlePricingInput());

        assertDoesNotThrow(() -> {
            BundlePriceResponse response = bundlePricingService.calculateTotalBundlePrice(request);
            assertScaledBigDecimal("50.00", response.getNetTotalAmount());
        });
    }

    @Test
    @DisplayName("Custom Attributes: Apply bundle discount based on external loyalty score")
    void calculateTotalBundlePrice_CustomAttributesLoyalty() {
        BundlePriceRequest request = BundlePriceRequest.builder()
                .productBundleId(1L)
                .products(List.of(BundlePriceRequest.BundleProductItem.builder().productId(10L).transactionAmount(BigDecimal.ZERO).build()))
                .customAttributes(Map.of("loyalty_score", 95))
                .build();

        when(productPricingService.getProductPricing(any())).thenReturn(
                ProductPricingCalculationResult.builder().finalChargeablePrice(new BigDecimal("100.00")).build());
        when(productBundleRepository.findById(any())).thenReturn(Optional.of(new ProductBundle()));
        when(bundlePricingLinkRepository.findByBundleIdAndDate(any(), any())).thenReturn(new ArrayList<>());

        // Simulate rules engine seeing the 95 score and returning a VIP discount
        BundlePricingInput rulesOutput = new BundlePricingInput();
        rulesOutput.addAdjustment("VIP_BUNDLE_WAIVER", new BigDecimal("100.00"), "DISCOUNT_PERCENTAGE");
        when(bundleRulesEngineService.determineBundleAdjustments(any())).thenReturn(rulesOutput);

        var response = bundlePricingService.calculateTotalBundlePrice(request);

        assertScaledBigDecimal("0.00", response.getNetTotalAmount(), "Net amount should be 0 after 100% loyalty waiver");
    }

    @DisplayName("Branch: Should handle null productTotalFee by treating as ZERO")
    void calculateTotalBundlePrice_shouldHandleNullProductPrice() {
        BundlePriceRequest request = BundlePriceRequest.builder()
                .productBundleId(1L)
                .products(List.of(new BundlePriceRequest.BundleProductItem(10L, BigDecimal.ZERO)))
                .build();

        // calcResult with null finalChargeablePrice
        when(productPricingService.getProductPricing(any())).thenReturn(
                ProductPricingCalculationResult.builder().finalChargeablePrice(null).build());
        when(productBundleRepository.findById(any())).thenReturn(Optional.of(new ProductBundle()));
        when(bundlePricingLinkRepository.findByBundleIdAndDate(any(), any())).thenReturn(List.of());
        when(bundleRulesEngineService.determineBundleAdjustments(any())).thenReturn(new BundlePricingInput());

        var response = bundlePricingService.calculateTotalBundlePrice(request);

        assertScaledBigDecimal("0.00", response.getNetTotalAmount());
    }

    @Test
    @DisplayName("Should throw IllegalStateException if ProductPricingService returns null result")
    void calculateTotalBundlePrice_shouldThrowExceptionOnNullResult() {
        BundlePriceRequest request = BundlePriceRequest.builder()
                .productBundleId(1L)
                .products(List.of(new BundlePriceRequest.BundleProductItem(10L, BigDecimal.ZERO)))
                .build();

        when(productPricingService.getProductPricing(any())).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> bundlePricingService.calculateTotalBundlePrice(request));
    }

    @Test
    @DisplayName("Should handle null adjustments from Rules Engine")
    void calculateTotalBundlePrice_shouldHandleNullAdjustmentsFromRules() {
        BundlePriceRequest request = BundlePriceRequest.builder()
                .productBundleId(1L)
                .products(List.of(new BundlePriceRequest.BundleProductItem(10L, BigDecimal.ZERO)))
                .build();

        when(productPricingService.getProductPricing(any())).thenReturn(
                ProductPricingCalculationResult.builder().finalChargeablePrice(new BigDecimal("100.00")).build());
        when(productBundleRepository.findById(any())).thenReturn(Optional.of(new ProductBundle()));
        when(bundlePricingLinkRepository.findByBundleIdAndDate(any(), any())).thenReturn(List.of());

        BundlePricingInput rulesOutput = new BundlePricingInput();
        rulesOutput.setAdjustments(null); // Explicitly null
        when(bundleRulesEngineService.determineBundleAdjustments(any())).thenReturn(rulesOutput);

        var response = bundlePricingService.calculateTotalBundlePrice(request);

        assertScaledBigDecimal("100.00", response.getNetTotalAmount());
        assertTrue(response.getBundleAdjustments().isEmpty());
    }

    @Test
    @DisplayName("Feature: Should pass custom attributes to Bundle Rules")
    void calculateTotalBundlePrice_shouldPassCustomAttributesToRules() {
        BundlePriceRequest request = BundlePriceRequest.builder()
                .productBundleId(1L)
                .products(List.of(new BundlePriceRequest.BundleProductItem(10L, BigDecimal.ZERO)))
                .customAttributes(java.util.Map.of("isNewCustomer", true))
                .build();

        when(productPricingService.getProductPricing(any())).thenReturn(
                ProductPricingCalculationResult.builder().finalChargeablePrice(new BigDecimal("100.00")).build());
        when(productBundleRepository.findById(any())).thenReturn(Optional.of(new ProductBundle()));
        when(bundlePricingLinkRepository.findByBundleIdAndDate(any(), any())).thenReturn(List.of());

        BundlePricingInput rulesOutput = new BundlePricingInput();
        when(bundleRulesEngineService.determineBundleAdjustments(any())).thenAnswer(invocation -> {
            BundlePricingInput input = invocation.getArgument(0);
            assertEquals(true, input.getCustomAttributes().get("isNewCustomer"));
            return rulesOutput;
        });

        bundlePricingService.calculateTotalBundlePrice(request);
    }

    @Test
    @DisplayName("Should handle fixed bundle link with null valueType")
    void calculateTotalBundlePrice_shouldHandleNullValueType() {
        BundlePriceRequest request = BundlePriceRequest.builder()
                .productBundleId(1L)
                .products(List.of(new BundlePriceRequest.BundleProductItem(10L, BigDecimal.ZERO)))
                .build();

        when(productPricingService.getProductPricing(any())).thenReturn(
                ProductPricingCalculationResult.builder().finalChargeablePrice(new BigDecimal("100.00")).build());
        when(productBundleRepository.findById(any())).thenReturn(Optional.of(new ProductBundle()));

        BundlePricingLink link = BundlePricingLink.builder()
                .pricingComponent(PricingComponent.builder().name("TEST_FEE").build())
                .fixedValue(new BigDecimal("10.00"))
                .fixedValueType(null) // Should default to FEE_ABSOLUTE
                .build();

        when(bundlePricingLinkRepository.findByBundleIdAndDate(any(), any())).thenReturn(List.of(link));
        when(bundleRulesEngineService.determineBundleAdjustments(any())).thenReturn(new BundlePricingInput());

        var response = bundlePricingService.calculateTotalBundlePrice(request);

        assertEquals(PriceValue.ValueType.FEE_ABSOLUTE, response.getBundleAdjustments().getFirst().getValueType());
        assertScaledBigDecimal("110.00", response.getNetTotalAmount());
    }

    // -----------------------------------------------------------------------------------
    // HELPERS
    // -----------------------------------------------------------------------------------

    private void assertScaledBigDecimal(String expected, BigDecimal actual) {
        assertScaledBigDecimal(expected, actual, null);
    }

    private void assertScaledBigDecimal(String expected, BigDecimal actual, String message) {
        BigDecimal expectedScaled = new BigDecimal(expected).setScale(2, RoundingMode.HALF_UP);
        assertEquals(expectedScaled, actual.setScale(2, RoundingMode.HALF_UP), message);
    }

    private BundlePricingLink createLink(String name, String value) {
        return BundlePricingLink.builder()
                .pricingComponent(PricingComponent.builder().name(name).build())
                .fixedValue(new BigDecimal(value))
                .fixedValueType(PriceValue.ValueType.FEE_ABSOLUTE)
                .build();
    }
}