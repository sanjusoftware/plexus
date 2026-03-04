package com.bankengine.pricing.service;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.dto.ProductPricingRequest;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.ProductPricingLink;
import com.bankengine.pricing.repository.ProductPricingLinkRepository;
import com.bankengine.rules.model.PricingInput;
import com.bankengine.rules.service.KieContainerReloadService;
import com.bankengine.test.config.BaseServiceTest;
import com.bankengine.web.exception.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductPricingServiceTest extends BaseServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductPricingLinkRepository productPricingLinkRepository;
    @Mock private PriceAggregator priceAggregator;
    @Mock private KieContainerReloadService kieContainerReloadService;

    @InjectMocks private ProductPricingService productPricingService;

    private ProductPricingRequest request;
    private MockedStatic<TenantContextHolder> mockedBankContext;

    @BeforeEach
    void setUp() {
        mockedBankContext = Mockito.mockStatic(TenantContextHolder.class);
        mockedBankContext.when(TenantContextHolder::getBankId).thenReturn("TEST_BANK");

        request = ProductPricingRequest.builder()
                .productId(1L)
                .transactionAmount(new BigDecimal("1000.00"))
                .effectiveDate(LocalDate.now())
                .customerSegment("RETAIL")
                .build();

        Product mockProduct = new Product();
        mockProduct.setId(1L);
        lenient().when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
    }

    @AfterEach
    void tearDown() {
        mockedBankContext.close();
    }

    @Test
    @DisplayName("Should orchestrate fixed pricing collection and call aggregator with productBaseFee")
    void getProductPricing_shouldOrchestrateCollectionAndAggregation() {
        // Arrange
        ProductPricingLink fixedLink = createLink(101L, "FixedFee", new BigDecimal("10.00"), PriceValue.ValueType.FEE_ABSOLUTE, false);
        when(productPricingLinkRepository.findByProductIdAndDate(eq(1L), any(LocalDate.class))).thenReturn(List.of(fixedLink));
        when(priceAggregator.calculateBundleImpact(anyList(), any(BigDecimal.class), any(BigDecimal.class), any(), any()))
                .thenReturn(new BigDecimal("10.00"));

        // Act
        ProductPricingCalculationResult result = productPricingService.getProductPricing(request);

        // Assert
        assertNotNull(result);
        // The price is JUST the fee ($10.00)
        assertEquals(new BigDecimal("10.00"), result.getFinalChargeablePrice());

        // Verify: principal is $1000, pool is ZERO
        verify(priceAggregator).calculateBundleImpact(
                argThat(list -> list.size() == 1),
                eq(new BigDecimal("1000.00")),
                eq(BigDecimal.ZERO),
                isNull(),
                eq(request.getEffectiveDate()));
    }

    @Test
    @DisplayName("Should include DISCOUNT_PERCENTAGE and FREE_COUNT types in the breakdown")
    void getProductPricing_shouldIncludeWaivedAndFreeCountInBreakdown() {
        ProductPricingLink rulesLink = createLink(202L, "RulesComp", null, null, true);
        when(productPricingLinkRepository.findByProductIdAndDate(eq(1L), any(LocalDate.class))).thenReturn(List.of(rulesLink));

        KieSession mockSession = setupMockDrools();
        PriceValue feeFact = createFact("PROCESSING_FEE", "10.00", PriceValue.ValueType.FEE_ABSOLUTE);
        PriceValue discountFact = createFact("ANNUAL_FEE_WAIVED", "100.00", PriceValue.ValueType.DISCOUNT_PERCENTAGE);
        PriceValue freeCountFact = createFact("ATM_FREE_WITHDRAWALS", "5.00", PriceValue.ValueType.FREE_COUNT);

        when(mockSession.getObjects(any())).thenReturn((Collection) List.of(feeFact, discountFact, freeCountFact));
        when(priceAggregator.calculateBundleImpact(anyList(), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        ProductPricingCalculationResult result = productPricingService.getProductPricing(request);

        assertEquals(3, result.getComponentBreakdown().size());
        assertTrue(result.getComponentBreakdown().stream().anyMatch(d -> d.getValueType() == PriceValue.ValueType.DISCOUNT_PERCENTAGE));
        assertTrue(result.getComponentBreakdown().stream().anyMatch(d -> d.getValueType() == PriceValue.ValueType.FREE_COUNT));
    }

    @Test
    @DisplayName("Should only send component IDs marked for rules engine to Drools")
    void getProductPricing_shouldTargetOnlyRuleBasedComponentIds() {
        ProductPricingLink fixed = createLink(1L, "Fixed", new BigDecimal("10.00"), PriceValue.ValueType.FEE_ABSOLUTE, false);
        ProductPricingLink rules = createLink(2L, "Rules", null, null, true);

        when(productPricingLinkRepository.findByProductIdAndDate(eq(1L), any(LocalDate.class))).thenReturn(List.of(fixed, rules));
        KieSession mockSession = setupMockDrools();
        when(priceAggregator.calculateBundleImpact(anyList(), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        productPricingService.getProductPricing(request);

        ArgumentCaptor<PricingInput> inputCaptor = ArgumentCaptor.forClass(PricingInput.class);
        verify(mockSession).insert(inputCaptor.capture());

        Set<Long> targetIds = inputCaptor.getValue().getTargetPricingComponentIds();
        assertTrue(targetIds.contains(2L));
        assertFalse(targetIds.contains(1L));
    }

    @Test
    @DisplayName("Should throw NotFoundException if no links exist for product")
    void getProductPricing_shouldThrowExceptionWhenNoLinks() {
        when(productPricingLinkRepository.findByProductIdAndDate(anyLong(), any())).thenReturn(List.of());
        assertThrows(NotFoundException.class, () -> productPricingService.getProductPricing(request));
    }

    @Test
    @DisplayName("DSK: Should propagate Tier flags (Pro-rata/Breach) to Aggregator")
    void getProductPricing_shouldPropagateNewTierFlags() {
        ProductPricingLink rulesLink = createLink(300L, "DskComp", null, null, true);
        when(productPricingLinkRepository.findByProductIdAndDate(anyLong(), any())).thenReturn(List.of(rulesLink));

        KieSession mockSession = setupMockDrools();
        PricingTier dskTier = new PricingTier();
        dskTier.setProRataApplicable(true);
        dskTier.setApplyChargeOnFullBreach(true);

        PriceValue fact = createFact("DSK_FEE", "50.00", PriceValue.ValueType.FEE_ABSOLUTE);
        fact.setPricingTier(dskTier);

        when(mockSession.getObjects(any())).thenReturn((Collection) List.of(fact));
        when(priceAggregator.calculateBundleImpact(anyList(), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        productPricingService.getProductPricing(request);

        verify(priceAggregator).calculateBundleImpact(argThat(list ->
                list.getFirst().isProRataApplicable() && list.getFirst().isApplyChargeOnFullBreach()
        ), eq(new BigDecimal("1000.00")), eq(BigDecimal.ZERO), any(), any());
    }

    @Test
    @DisplayName("Branch: Should handle PriceValue with null PricingTier (Defensive Mapping)")
    void mapFactToDetail_shouldHandleNullTier() {
        ProductPricingLink rulesLink = createLink(600L, "NoTierComp", null, null, true);
        when(productPricingLinkRepository.findByProductIdAndDate(anyLong(), any())).thenReturn(List.of(rulesLink));

        KieSession mockSession = setupMockDrools();
        PriceValue fact = createFact("ORPHAN_FEE", "20.00", PriceValue.ValueType.FEE_ABSOLUTE);
        fact.setPricingTier(null);

        when(mockSession.getObjects(any())).thenReturn((Collection) List.of(fact));
        when(priceAggregator.calculateBundleImpact(anyList(), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        ProductPricingCalculationResult result = productPricingService.getProductPricing(request);

        assertFalse(result.getComponentBreakdown().getFirst().isProRataApplicable());
        assertFalse(result.getComponentBreakdown().getFirst().isApplyChargeOnFullBreach());
    }

    @Test
    @DisplayName("Branch: Should filter out links marked as FIXED but having no value")
    void getProductPricing_shouldFilterEmptyFixedLinks() {
        ProductPricingLink emptyFixedLink = createLink(900L, "EmptyFixed", null, null, false);
        ProductPricingLink validLink = createLink(901L, "ValidFixed", new BigDecimal("5.00"), PriceValue.ValueType.FEE_ABSOLUTE, false);

        when(productPricingLinkRepository.findByProductIdAndDate(anyLong(), any())).thenReturn(List.of(emptyFixedLink, validLink));
        when(priceAggregator.calculateBundleImpact(anyList(), any(), any(), any(), any()))
                .thenReturn(new BigDecimal("5.00"));

        ProductPricingCalculationResult result = productPricingService.getProductPricing(request);

        assertEquals(1, result.getComponentBreakdown().size());
        assertEquals(new BigDecimal("5.00"), result.getFinalChargeablePrice());
    }

    @Test
    @DisplayName("Branch: Should handle null transactionAmount by treating as ZERO")
    void getProductPricing_shouldHandleNullTransactionAmount() {
        request.setTransactionAmount(null);
        ProductPricingLink fixedLink = createLink(101L, "FixedFee", new BigDecimal("10.00"), PriceValue.ValueType.FEE_ABSOLUTE, false);
        when(productPricingLinkRepository.findByProductIdAndDate(anyLong(), any())).thenReturn(List.of(fixedLink));
        when(priceAggregator.calculateBundleImpact(anyList(), any(), any(), any(), any())).thenReturn(new BigDecimal("10.00"));

        ProductPricingCalculationResult result = productPricingService.getProductPricing(request);

        verify(priceAggregator).calculateBundleImpact(anyList(), eq(BigDecimal.ZERO), eq(BigDecimal.ZERO), any(), any());
        assertEquals(new BigDecimal("10.00"), result.getFinalChargeablePrice());
    }

    @Test
    @DisplayName("Feature: Should pass custom attributes to Drools")
    void getProductPricing_shouldPassCustomAttributesToDrools() {
        request.setCustomAttributes(java.util.Map.of("yearsOfService", 5));
        ProductPricingLink rulesLink = createLink(202L, "RulesComp", null, null, true);
        when(productPricingLinkRepository.findByProductIdAndDate(eq(1L), any(LocalDate.class))).thenReturn(List.of(rulesLink));

        KieSession mockSession = setupMockDrools();
        when(priceAggregator.calculateBundleImpact(anyList(), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        productPricingService.getProductPricing(request);

        ArgumentCaptor<PricingInput> inputCaptor = ArgumentCaptor.forClass(PricingInput.class);
        verify(mockSession).insert(inputCaptor.capture());
        PricingInput input = inputCaptor.getValue();

        assertEquals(5, input.getCustomAttributes().get("yearsOfService"));
        assertEquals(1L, input.getCustomAttributes().get("productId"));
    }

    // --- Helper Methods ---

    private KieSession setupMockDrools() {
        KieContainer mockContainer = mock(KieContainer.class);
        KieSession mockSession = mock(KieSession.class);
        when(kieContainerReloadService.getKieContainer()).thenReturn(mockContainer);
        when(mockContainer.newKieSession()).thenReturn(mockSession);
        return mockSession;
    }

    private ProductPricingLink createLink(Long id, String name, BigDecimal val, PriceValue.ValueType type, boolean useRules) {
        PricingComponent comp = new PricingComponent();
        comp.setId(id);
        comp.setName(name);
        ProductPricingLink link = new ProductPricingLink();
        link.setPricingComponent(comp);
        link.setFixedValue(val);
        link.setFixedValueType(type);
        link.setUseRulesEngine(useRules);
        return link;
    }

    private PriceValue createFact(String code, String val, PriceValue.ValueType type) {
        PriceValue pv = new PriceValue();
        pv.setComponentCode(code);
        pv.setRawValue(new BigDecimal(val));
        pv.setValueType(type);
        pv.setPricingTier(new PricingTier());
        return pv;
    }
}