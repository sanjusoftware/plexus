package com.bankengine.pricing.service;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.pricing.dto.PricingRequest;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
                .customerSegment("RETAIL")
                .build();

        // Satisfy getByIdSecurely check for successful tests
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
        
        when(productPricingLinkRepository.findByProductId(1L)).thenReturn(List.of(fixedLink));
        when(priceAggregator.calculate(anyList(), any(BigDecimal.class))).thenReturn(new BigDecimal("10.00"));

        ProductPricingCalculationResult result = pricingCalculationService.getProductPricing(request);
        
        assertNotNull(result);
        assertEquals(new BigDecimal("10.00"), result.getFinalChargeablePrice());
        verify(priceAggregator).calculate(argThat(list -> list.size() == 1), eq(new BigDecimal("1000.00")));
    }

    @Test
    @DisplayName("Should include WAIVED and FREE_COUNT types in the breakdown")
    void getProductPricing_shouldIncludeWaivedAndFreeCountInBreakdown() {
        ProductPricingLink rulesLink = createLink(202L, "RulesComp", null, null, true);
        when(productPricingLinkRepository.findByProductId(1L)).thenReturn(List.of(rulesLink));

        KieSession mockSession = setupMockDrools();

        PriceValue feeFact = createFact("PROCESSING_FEE", "10.00", PriceValue.ValueType.FEE_ABSOLUTE);
        PriceValue waivedFact = createFact("ANNUAL_FEE_WAIVED", "0.00", PriceValue.ValueType.WAIVED);
        PriceValue freeCountFact = createFact("ATM_FREE_WITHDRAWALS", "5.00", PriceValue.ValueType.FREE_COUNT);

        when(mockSession.getObjects(any())).thenReturn((Collection) List.of(feeFact, waivedFact, freeCountFact));
        when(priceAggregator.calculate(anyList(), any(BigDecimal.class))).thenReturn(new BigDecimal("10.00"));

        ProductPricingCalculationResult result = pricingCalculationService.getProductPricing(request);

        assertEquals(3, result.getComponentBreakdown().size());
        assertTrue(result.getComponentBreakdown().stream().anyMatch(d -> d.getValueType() == PriceValue.ValueType.WAIVED));
        assertTrue(result.getComponentBreakdown().stream().anyMatch(d -> d.getValueType() == PriceValue.ValueType.FREE_COUNT));
    }

    // --- TEST 3: Targeting Logic (New Requirement) ---
    @Test
    @DisplayName("Should only send component IDs marked for rules engine to Drools")
    void getProductPricing_shouldTargetOnlyRuleBasedComponentIds() {
        ProductPricingLink fixed = createLink(1L, "Fixed", new BigDecimal("10.00"), PriceValue.ValueType.FEE_ABSOLUTE, false);
        ProductPricingLink rules = createLink(2L, "Rules", null, null, true);
        
        when(productPricingLinkRepository.findByProductId(1L)).thenReturn(List.of(fixed, rules));
        KieSession mockSession = setupMockDrools();

        pricingCalculationService.getProductPricing(request);

        ArgumentCaptor<PricingInput> inputCaptor = ArgumentCaptor.forClass(PricingInput.class);
        verify(mockSession).insert(inputCaptor.capture());
        
        Set<Long> targetIds = inputCaptor.getValue().getTargetPricingComponentIds();
        assertTrue(targetIds.contains(2L));
        assertFalse(targetIds.contains(1L));
    }

    // --- TEST 4: Exception Handling (Standard Practice) ---
    @Test
    @DisplayName("Should throw NotFoundException if no links exist for product")
    void getProductPricing_shouldThrowExceptionWhenNoLinks() {
        when(productPricingLinkRepository.findByProductId(anyLong())).thenReturn(List.of());
        assertThrows(NotFoundException.class, () -> pricingCalculationService.getProductPricing(request));
    }

    @Test
    @DisplayName("Should only whitelist component IDs found in the database links")
    void getProductPricing_shouldIgnoreNonTargetedRules() {
        // 1. ARRANGE: DB has 2 links for this product (101 and 102)
        ProductPricingLink link1 = createLink(101L, "Fee_A", null, null, true);
        ProductPricingLink link2 = createLink(102L, "Fee_B", null, null, true);
        when(productPricingLinkRepository.findByProductId(1L)).thenReturn(List.of(link1, link2));

        KieSession mockSession = setupMockDrools();

        // 2. ACT
        pricingCalculationService.getProductPricing(request);

        // 3. ASSERT: Capture the input sent to the Rules Engine
        ArgumentCaptor<PricingInput> inputCaptor = ArgumentCaptor.forClass(PricingInput.class);
        verify(mockSession).insert(inputCaptor.capture());

        Set<Long> sentIds = inputCaptor.getValue().getTargetPricingComponentIds();

        // Verify the whitelist only contains what the Service found in the Repository
        assertEquals(2, sentIds.size(), "Should only have 2 target IDs");
        assertTrue(sentIds.contains(101L));
        assertTrue(sentIds.contains(102L));

        // This confirms that even if a Rule for 999 exists in the DRL,
        // it won't fire because the Service didn't authorize it.
        assertFalse(sentIds.contains(999L), "ID 999 must not be sent if not linked to product");
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
        return pv;
    }
}