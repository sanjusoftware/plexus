package com.bankengine.pricing.service;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.pricing.dto.PricingRequest;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.ProductPricingLink;
import com.bankengine.pricing.repository.ProductPricingLinkRepository;
import com.bankengine.rules.service.KieContainerReloadService;
import com.bankengine.test.config.BaseServiceTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingCalculationServiceTest extends BaseServiceTest {

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
    }

    @AfterEach
    void tearDown() {
        mockedBankContext.close();
    }

    @Test
    void getProductPricing_shouldOrchestrateCollectionAndAggregation() {
        // ARRANGE
        ProductPricingLink fixedLink = createLink("FixedFee", new BigDecimal("10.00"), false);
        when(productPricingLinkRepository.findByProductId(anyLong()))
                .thenReturn(List.of(fixedLink));
        when(priceAggregator.calculate(anyList(), any(BigDecimal.class)))
                .thenReturn(new BigDecimal("10.00"));
        ProductPricingCalculationResult result = pricingCalculationService.getProductPricing(request);
        assertNotNull(result);
        assertEquals(new BigDecimal("10.00"), result.getFinalChargeablePrice());
        verify(priceAggregator).calculate(argThat(list -> list.size() == 1), eq(request.getAmount()));
    }

    @Test
    void getProductPricing_shouldIncludeWaivedAndFreeCountInBreakdown() {
        // 1. ARRANGE: Set up a rule-based link
        ProductPricingLink rulesLink = new ProductPricingLink();
        rulesLink.setUseRulesEngine(true);
        rulesLink.setPricingComponent(new PricingComponent("RULES_COMP", PricingComponent.ComponentType.FEE));

        when(productPricingLinkRepository.findByProductId(1L))
                .thenReturn(List.of(rulesLink));

        org.kie.api.runtime.KieContainer mockContainer = Mockito.mock(org.kie.api.runtime.KieContainer.class);
        org.kie.api.runtime.KieSession mockSession = Mockito.mock(org.kie.api.runtime.KieSession.class);

        when(kieContainerReloadService.getKieContainer()).thenReturn(mockContainer);
        when(mockContainer.newKieSession()).thenReturn(mockSession);

        // Create the facts to be returned
        PriceValue feeFact = new PriceValue(null, new BigDecimal("10.00"), PriceValue.ValueType.ABSOLUTE);
        feeFact.setComponentCode("PROCESSING_FEE");

        PriceValue waivedFact = new PriceValue(null, new BigDecimal("0.00"), PriceValue.ValueType.WAIVED);
        waivedFact.setComponentCode("ANNUAL_FEE_WAIVED");

        PriceValue freeCountFact = new PriceValue(null, new BigDecimal("5"), PriceValue.ValueType.FREE_COUNT);
        freeCountFact.setComponentCode("ATM_FREE_WITHDRAWALS");

        when(mockSession.getObjects(any(org.kie.api.runtime.ObjectFilter.class)))
                .thenReturn((Collection) List.of(feeFact, waivedFact, freeCountFact));

        // Stub the aggregator - it should only calculate based on the components provided
        when(priceAggregator.calculate(anyList(), any()))
                .thenReturn(new BigDecimal("10.00"));

        // 3. ACT
        ProductPricingCalculationResult result = pricingCalculationService.getProductPricing(request);

        // 4. ASSERT
        assertNotNull(result);
        assertEquals(3, result.getComponentBreakdown().size());

        boolean hasWaived = result.getComponentBreakdown().stream()
                .anyMatch(d -> d.getValueType() == PriceValue.ValueType.WAIVED);
        boolean hasFreeCount = result.getComponentBreakdown().stream()
                .anyMatch(d -> d.getValueType() == PriceValue.ValueType.FREE_COUNT);

        assertTrue(hasWaived, "WAIVED component missing from breakdown");
        assertTrue(hasFreeCount, "FREE_COUNT component missing from breakdown");
    }

    private ProductPricingLink createLink(String name, BigDecimal val, boolean useRules) {
        PricingComponent comp = new PricingComponent();
        comp.setName(name);
        ProductPricingLink link = new ProductPricingLink();
        link.setPricingComponent(comp);
        link.setFixedValue(val);
        link.setUseRulesEngine(useRules);
        return link;
    }
}