package com.bankengine.pricing.service;

import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.common.service.BaseService;
import com.bankengine.pricing.dto.PricingRequest;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.dto.ProductPricingCalculationResult.PriceComponentDetail;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.ProductPricingLink;
import com.bankengine.pricing.repository.ProductPricingLinkRepository;
import com.bankengine.rules.model.PricingInput;
import com.bankengine.rules.service.KieContainerReloadService;
import com.bankengine.web.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.kie.api.runtime.ClassObjectFilter;
import org.kie.api.runtime.KieSession;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PricingCalculationService extends BaseService {

    private final KieContainerReloadService kieContainerReloadService;
    private final ProductRepository productRepository;
    private final ProductPricingLinkRepository productPricingLinkRepository;
    private final PriceAggregator priceAggregator;

    @Transactional(readOnly = true)
    public ProductPricingCalculationResult getProductPricing(PricingRequest request) {
        // 1. SECURE FETCH: Verify tenant ownership
        getByIdSecurely(productRepository, request.getProductId(), "Product");

        // 2. FETCH LINKS (Date-aware for mid-cycle requirement)
        List<ProductPricingLink> links = getCachedLinks(request.getProductId(), request.getEffectiveDate());

        if (links.isEmpty()) {
            throw new NotFoundException("No active pricing configuration found for product ID: "
                + request.getProductId() + " on date: " + request.getEffectiveDate());
        }

        List<PriceComponentDetail> allComponents = new ArrayList<>();

        // 3. Process Fixed
        links.stream()
                .filter(link -> !link.isUseRulesEngine() && link.getFixedValue() != null)
                .map(this::mapFixedLinkToDetail)
                .forEach(allComponents::add);

        // 4. Process Rules
        List<ProductPricingLink> ruleBasedLinks = links.stream()
                .filter(ProductPricingLink::isUseRulesEngine).toList();

        if (!ruleBasedLinks.isEmpty()) {
            Set<Long> targetIds = ruleBasedLinks.stream()
                    .map(l -> l.getPricingComponent().getId())
                    .collect(Collectors.toSet());

            determinePriceWithDrools(request, targetIds).stream()
                    .map(this::mapFactToDetail)
                    .forEach(allComponents::add);
        }

        // 5. Aggregate logic handles pro-rata and slab breaching
        BigDecimal finalPrice = priceAggregator.calculate(allComponents, request);

        return ProductPricingCalculationResult.builder()
                .finalChargeablePrice(finalPrice)
                .componentBreakdown(allComponents)
                .build();
    }

    /**
     * Updated Cache Key to include effectiveDate to prevent returning stale/future rules.
     */
    @Cacheable(value = "productPricingLinks",
            key = "T(com.bankengine.auth.security.TenantContextHolder).getBankId() + '_' + #productId + '_' + #effectiveDate")
    private List<ProductPricingLink> getCachedLinks(Long productId, LocalDate effectiveDate) {
        return productPricingLinkRepository.findByProductIdAndDate(productId, effectiveDate);
    }

    private PriceComponentDetail mapFixedLinkToDetail(ProductPricingLink link) {
        return PriceComponentDetail.builder()
                .componentCode(link.getPricingComponent().getName())
                .rawValue(link.getFixedValue())
                .valueType(link.getFixedValueType())
                .sourceType("FIXED_VALUE")
                .targetComponentCode(link.getTargetComponentCode())
                .proRataApplicable(false)
                .applyChargeOnFullBreach(false)
                .build();
    }

    private PriceComponentDetail mapFactToDetail(PriceValue fact) {
        // Defensive check: Drools might return a PriceValue with no Tier association
        boolean proRata = fact.getPricingTier() != null && fact.getPricingTier().isProRataApplicable();
        boolean fullBreach = fact.getPricingTier() != null && fact.getPricingTier().isApplyChargeOnFullBreach();

        return PriceComponentDetail.builder()
                .componentCode(fact.getComponentCode())
                .rawValue(fact.getRawValue())
                .valueType(fact.getValueType())
                .sourceType("RULES_ENGINE")
                .matchedTierId(fact.getMatchedTierId())
                .proRataApplicable(proRata)
                .applyChargeOnFullBreach(fullBreach)
                .build();
    }

    private Collection<PriceValue> determinePriceWithDrools(PricingRequest request, Set<Long> targetIds) {
        KieSession kieSession = kieContainerReloadService.getKieContainer().newKieSession();
        PricingInput input = new PricingInput();
        input.setBankId(getCurrentBankId());
        input.setTargetPricingComponentIds(targetIds);

        input.setReferenceDate(request.getEffectiveDate());

        if (request.getCustomAttributes() != null) {
            input.getCustomAttributes().putAll(request.getCustomAttributes());
        }

        // Context injection for Rules Engine
        input.getCustomAttributes().put("productId", request.getProductId());
        input.getCustomAttributes().put("customerSegment", request.getCustomerSegment());
        input.getCustomAttributes().put("transactionAmount", request.getAmount());
        input.getCustomAttributes().put("effectiveDate", request.getEffectiveDate());

        try {
            kieSession.insert(input);
            kieSession.fireAllRules();
            return kieSession.getObjects(new ClassObjectFilter(PriceValue.class))
                    .stream().map(PriceValue.class::cast).toList();
        } finally {
            kieSession.dispose();
        }
    }
}