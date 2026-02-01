package com.bankengine.pricing.service;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.pricing.dto.PricingRequest;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PricingCalculationServiceTest extends BaseServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductPricingLinkRepository productPricingLinkRepository;
    @Mock
    private PriceAggregator priceAggregator;
    @Mock
    private KieContainerReloadService kieContainerReloadService;

    @InjectMocks
    private PricingCalculationService pricingCalculationService;

    private PricingRequest request;
    private MockedStatic<TenantContextHolder> mockedBankContext;

    @BeforeEach
    void setUp() {
        mockedBankContext = Mockito.mockStatic(TenantContextHolder.class);
        mockedBankContext.when(TenantContextHolder::getBankId).thenReturn("TEST_BANK");

        request = PricingRequest.builder()
                .productId(1L)
                .amount(new BigDecimal("1000.00"))
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
    @DisplayName("Should orchestrate fixed pricing collection and call aggregator")
    void getProductPricing_shouldOrchestrateCollectionAndAggregation() {
        ProductPricingLink fixedLink = createLink(101L, "FixedFee", new BigDecimal("10.00"), PriceValue.ValueType.FEE_ABSOLUTE, false);

        when(productPricingLinkRepository.findByProductIdAndDate(eq(1L), any(LocalDate.class))).thenReturn(List.of(fixedLink));
        when(priceAggregator.calculate(anyList(), any(PricingRequest.class))).thenReturn(new BigDecimal("10.00"));

        ProductPricingCalculationResult result = pricingCalculationService.getProductPricing(request);

        assertNotNull(result);
        assertEquals(new BigDecimal("10.00"), result.getFinalChargeablePrice());
        verify(priceAggregator).calculate(argThat(list -> list.size() == 1), eq(request));
    }

    @Test
    @DisplayName("Should include WAIVED and FREE_COUNT types in the breakdown")
    void getProductPricing_shouldIncludeWaivedAndFreeCountInBreakdown() {
        ProductPricingLink rulesLink = createLink(202L, "RulesComp", null, null, true);
        when(productPricingLinkRepository.findByProductIdAndDate(eq(1L), any(LocalDate.class))).thenReturn(List.of(rulesLink));

        KieSession mockSession = setupMockDrools();

        PriceValue feeFact = createFact("PROCESSING_FEE", "10.00", PriceValue.ValueType.FEE_ABSOLUTE);
        PriceValue waivedFact = createFact("ANNUAL_FEE_WAIVED", "0.00", PriceValue.ValueType.WAIVED);
        PriceValue freeCountFact = createFact("ATM_FREE_WITHDRAWALS", "5.00", PriceValue.ValueType.FREE_COUNT);

        when(mockSession.getObjects(any())).thenReturn((Collection) List.of(feeFact, waivedFact, freeCountFact));
        when(priceAggregator.calculate(anyList(), any(PricingRequest.class))).thenReturn(new BigDecimal("10.00"));

        ProductPricingCalculationResult result = pricingCalculationService.getProductPricing(request);

        assertEquals(3, result.getComponentBreakdown().size());
        assertTrue(result.getComponentBreakdown().stream().anyMatch(d -> d.getValueType() == PriceValue.ValueType.WAIVED));
        assertTrue(result.getComponentBreakdown().stream().anyMatch(d -> d.getValueType() == PriceValue.ValueType.FREE_COUNT));
    }

    @Test
    @DisplayName("Should only send component IDs marked for rules engine to Drools")
    void getProductPricing_shouldTargetOnlyRuleBasedComponentIds() {
        ProductPricingLink fixed = createLink(1L, "Fixed", new BigDecimal("10.00"), PriceValue.ValueType.FEE_ABSOLUTE, false);
        ProductPricingLink rules = createLink(2L, "Rules", null, null, true);

        when(productPricingLinkRepository.findByProductIdAndDate(eq(1L), any(LocalDate.class))).thenReturn(List.of(fixed, rules));
        KieSession mockSession = setupMockDrools();

        pricingCalculationService.getProductPricing(request);

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
        assertThrows(NotFoundException.class, () -> pricingCalculationService.getProductPricing(request));
    }

    @Test
    @DisplayName("Should only whitelist component IDs found in the database links")
    void getProductPricing_shouldIgnoreNonTargetedRules() {
        ProductPricingLink link1 = createLink(101L, "Fee_A", null, null, true);
        ProductPricingLink link2 = createLink(102L, "Fee_B", null, null, true);
        when(productPricingLinkRepository.findByProductIdAndDate(eq(1L), any(LocalDate.class))).thenReturn(List.of(link1, link2));

        KieSession mockSession = setupMockDrools();

        pricingCalculationService.getProductPricing(request);

        ArgumentCaptor<PricingInput> inputCaptor = ArgumentCaptor.forClass(PricingInput.class);
        verify(mockSession).insert(inputCaptor.capture());

        Set<Long> sentIds = inputCaptor.getValue().getTargetPricingComponentIds();
        assertEquals(2, sentIds.size(), "Should only have 2 target IDs");
        assertTrue(sentIds.contains(101L));
        assertTrue(sentIds.contains(102L));
        assertFalse(sentIds.contains(999L));
    }

    @Test
    @DisplayName("DSK: Should propagate Tier flags (Pro-rata/Breach) to Aggregator")
    void getProductPricing_shouldPropagateNewTierFlags() {
        ProductPricingLink rulesLink = createLink(300L, "DskComp", null, null, true);
        when(productPricingLinkRepository.findByProductIdAndDate(anyLong(), any())).thenReturn(List.of(rulesLink));

        KieSession mockSession = setupMockDrools();

        // Define specific DSK tier behavior
        PricingTier dskTier = new PricingTier();
        dskTier.setProRataApplicable(true);
        dskTier.setApplyChargeOnFullBreach(true);

        PriceValue fact = createFact("DSK_FEE", "50.00", PriceValue.ValueType.FEE_ABSOLUTE);
        fact.setPricingTier(dskTier);

        when(mockSession.getObjects(any())).thenReturn((Collection) List.of(fact));

        pricingCalculationService.getProductPricing(request);

        // Verify aggregator receives the flags
        verify(priceAggregator).calculate(argThat(list ->
                list.get(0).isProRataApplicable() && list.get(0).isApplyChargeOnFullBreach()
        ), eq(request));
    }

    @Test
    @DisplayName("Temporal: Should use effectiveDate from request for link lookup")
    void getProductPricing_shouldUseRequestEffectiveDateForLookup() {
        LocalDate historicalDate = LocalDate.now().minusMonths(1);
        request.setEffectiveDate(historicalDate);

        ProductPricingLink oldLink = createLink(10L, "OldFee", new BigDecimal("5.00"), PriceValue.ValueType.FEE_ABSOLUTE, false);
        when(productPricingLinkRepository.findByProductIdAndDate(1L, historicalDate)).thenReturn(List.of(oldLink));

        pricingCalculationService.getProductPricing(request);

        verify(productPricingLinkRepository).findByProductIdAndDate(1L, historicalDate);
    }

    @Test
    @DisplayName("Branch: Should handle rules engine path when custom attributes are null")
    void getProductPricing_shouldHandleNullCustomAttributes() {
        request.setCustomAttributes(null); // Force the 'null' branch in determinePriceWithDrools
        ProductPricingLink rulesLink = createLink(500L, "NullAttrComp", null, null, true);

        when(productPricingLinkRepository.findByProductIdAndDate(anyLong(), any())).thenReturn(List.of(rulesLink));
        KieSession mockSession = setupMockDrools();

        pricingCalculationService.getProductPricing(request);

        ArgumentCaptor<PricingInput> inputCaptor = ArgumentCaptor.forClass(PricingInput.class);
        verify(mockSession).insert(inputCaptor.capture());
        assertNotNull(inputCaptor.getValue().getCustomAttributes());
        // Should still contain the default injected attributes (productId, etc.)
        assertTrue(inputCaptor.getValue().getCustomAttributes().containsKey("productId"));
    }

    @Test
    @DisplayName("Branch: Should handle PriceValue with null PricingTier (Defensive Mapping)")
    void mapFactToDetail_shouldHandleNullTier() {
        ProductPricingLink rulesLink = createLink(600L, "NoTierComp", null, null, true);
        when(productPricingLinkRepository.findByProductIdAndDate(anyLong(), any())).thenReturn(List.of(rulesLink));

        KieSession mockSession = setupMockDrools();

        // Create fact with NO tier associated
        PriceValue fact = createFact("ORPHAN_FEE", "20.00", PriceValue.ValueType.FEE_ABSOLUTE);
        fact.setPricingTier(null);

        when(mockSession.getObjects(any())).thenReturn((Collection) List.of(fact));
        when(priceAggregator.calculate(anyList(), any())).thenReturn(BigDecimal.ZERO);

        ProductPricingCalculationResult result = pricingCalculationService.getProductPricing(request);

        // Verify the mapping didn't crash and defaulted flags to false
        assertFalse(result.getComponentBreakdown().get(0).isProRataApplicable());
        assertFalse(result.getComponentBreakdown().get(0).isApplyChargeOnFullBreach());
    }

    @Test
    @DisplayName("Temporal: Should pass activeTierIds from repository to Drools input")
    void getProductPricing_shouldInjectActiveTierIdsIntoDrools() {
        ProductPricingLink rulesLink = createLink(700L, "TempComp", null, null, true);
        List<Long> mockTierIds = List.of(8001L, 8002L);

        when(productPricingLinkRepository.findByProductIdAndDate(anyLong(), any())).thenReturn(List.of(rulesLink));
        when(productPricingLinkRepository.findActiveTierIds(eq(1L), any(LocalDate.class))).thenReturn(mockTierIds);

        KieSession mockSession = setupMockDrools();

        pricingCalculationService.getProductPricing(request);

        ArgumentCaptor<PricingInput> inputCaptor = ArgumentCaptor.forClass(PricingInput.class);
        verify(mockSession).insert(inputCaptor.capture());

        // Assert the date-filtered Tier IDs are present
        assertEquals(2, inputCaptor.getValue().getActivePricingTierIds().size());
        assertTrue(inputCaptor.getValue().getActivePricingTierIds().contains(8001L));
    }

    @Test
    @DisplayName("Branch: Should merge non-null custom attributes into PricingInput")
    void getProductPricing_shouldMergeCustomAttributes() {
        // 1. Setup request with custom attributes
        request.setCustomAttributes(Map.of("promoCode", "SAVE10"));
        ProductPricingLink rulesLink = createLink(800L, "AttrComp", null, null, true);
        when(productPricingLinkRepository.findByProductIdAndDate(anyLong(), any())).thenReturn(List.of(rulesLink));
        KieSession mockSession = setupMockDrools();

        pricingCalculationService.getProductPricing(request);

        // 2. Verify merge happened
        ArgumentCaptor<PricingInput> inputCaptor = ArgumentCaptor.forClass(PricingInput.class);
        verify(mockSession).insert(inputCaptor.capture());
        assertEquals("SAVE10", inputCaptor.getValue().getCustomAttributes().get("promoCode"));
    }

    @Test
    @DisplayName("Branch: Should filter out links marked as FIXED but having no value")
    void getProductPricing_shouldFilterEmptyFixedLinks() {
        // Link marked as fixed (useRules=false) but has no value
        ProductPricingLink emptyFixedLink = createLink(900L, "EmptyFixed", null, null, false);
        // Link that is valid
        ProductPricingLink validLink = createLink(901L, "ValidFixed", new BigDecimal("5.00"), PriceValue.ValueType.FEE_ABSOLUTE, false);

        when(productPricingLinkRepository.findByProductIdAndDate(anyLong(), any())).thenReturn(List.of(emptyFixedLink, validLink));
        when(priceAggregator.calculate(anyList(), any())).thenReturn(new BigDecimal("5.00"));

        ProductPricingCalculationResult result = pricingCalculationService.getProductPricing(request);

        // Breakdown should only contain the valid link
        assertEquals(1, result.getComponentBreakdown().size());
        assertEquals("ValidFixed", result.getComponentBreakdown().get(0).getComponentCode());
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