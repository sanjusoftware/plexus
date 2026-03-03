package com.bankengine.pricing.service;

import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.common.service.BaseService;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.dto.ProductPricingCalculationResult.PriceComponentDetail;
import com.bankengine.pricing.dto.ProductPricingRequest;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.ProductPricingLink;
import com.bankengine.pricing.repository.ProductPricingLinkRepository;
import com.bankengine.rules.model.PricingInput;
import com.bankengine.rules.service.KieContainerReloadService;
import com.bankengine.web.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductPricingService extends BaseService {

    private final KieContainerReloadService kieContainerReloadService;
    private final ProductRepository productRepository;
    private final ProductPricingLinkRepository productPricingLinkRepository;
    private final PriceAggregator priceAggregator;

    /**
     * Live Pricing Entry Point: Fetches configuration from DB/Rules and calculates final price.
     */
    @Transactional(readOnly = true)
    public ProductPricingCalculationResult getProductPricing(ProductPricingRequest request) {
        // 0. Validation & Security
        verifyProductAccess(request.getProductId());

        // 1. Data Retrieval: Fetch active pricing links (Fixed and Rule-based definitions)
        List<ProductPricingLink> activePricingLinks = getActivePricingLinks(request);

        // 2. Component Assembly: Combine Static (DB) and Dynamic (Drools) components
        List<PriceComponentDetail> priceComponentDetails = assemblePricingComponents(request, activePricingLinks);

        // 3. Calculation: Delegate to the Pure Calculation Engine (PriceAggregator)
        // Here, the 'transactionAmount' in request is treated as the 'productFeeCalculationBaseAmount' for this specific product
        BigDecimal productFeeCalculationBaseAmount = (request.getTransactionAmount() != null) ? request.getTransactionAmount() : BigDecimal.ZERO;

        // Note: For a single product, the 'netImpact' is the sum of its fees and discounts
        BigDecimal netImpact = priceAggregator.calculateBundleImpact(
                priceComponentDetails,
                productFeeCalculationBaseAmount,
                BigDecimal.ZERO,
                request.getEnrollmentDate(),
                request.getEffectiveDate());

        // 4. Build Final Result
        // The PRICE of the product is the sum of the fees, NOT the transaction amount
        return ProductPricingCalculationResult.builder()
                .finalChargeablePrice(netImpact)
                .componentBreakdown(priceComponentDetails)
                .build();
    }

    // -----------------------------------------------------------------------------------
    // PRIVATE HELPER METHODS (Orchestration Steps)
    // -----------------------------------------------------------------------------------

    private void verifyProductAccess(Long productId) {
        getByIdSecurely(productRepository, productId, "Product");
    }

    private List<ProductPricingLink> getActivePricingLinks(ProductPricingRequest request) {
        List<ProductPricingLink> links = getCachedLinks(request.getProductId(), request.getEffectiveDate());
        if (links.isEmpty()) {
            throw new NotFoundException("No active pricing configuration found for product: "
                    + request.getProductId() + " on date: " + request.getEffectiveDate());
        }
        return links;
    }

    /**
     * Gathers all definitions (Fixed from DB and Tiers from Rules).
     */
    private List<PriceComponentDetail> assemblePricingComponents(ProductPricingRequest request, List<ProductPricingLink> links) {
        List<PriceComponentDetail> components = new ArrayList<>();

        // Process Static Fixed Values
        links.stream()
                .filter(link -> !link.isUseRulesEngine() && link.getFixedValue() != null)
                .map(this::mapFixedLinkToDetail)
                .forEach(components::add);

        // Process Dynamic Rule-Based Tiers
        List<ProductPricingLink> ruleBasedLinks = links.stream()
                .filter(ProductPricingLink::isUseRulesEngine).toList();

        if (!ruleBasedLinks.isEmpty()) {
            components.addAll(retrieveDynamicComponents(request, ruleBasedLinks));
        }

        return components;
    }

    private List<PriceComponentDetail> retrieveDynamicComponents(ProductPricingRequest request, List<ProductPricingLink> ruleLinks) {
        // 1. Identify Target Component IDs
        Set<Long> componentIds = ruleLinks.stream()
                .map(l -> l.getPricingComponent().getId())
                .collect(Collectors.toSet());

        // 2. REFACTORED: Harvest Tier IDs directly from the active component links.
        // Since Tiers follow Component activation dates, we collect all tiers belonging to these active components.
        Set<Long> activeTierIds = ruleLinks.stream()
                .flatMap(l -> l.getPricingComponent().getPricingTiers().stream())
                .map(PricingTier::getId)
                .collect(Collectors.toSet());

        // 3. Execute Rules with fully populated ID sets
        return determinePriceWithDrools(request, componentIds, activeTierIds).stream()
                .map(this::mapFactToDetail)
                .toList();
    }

    private Collection<PriceValue> determinePriceWithDrools(ProductPricingRequest request, Set<Long> componentIds, Set<Long> activeTierIds) {
        KieSession kieSession = kieContainerReloadService.getKieContainer().newKieSession();
        try {
            PricingInput input = new PricingInput();
            input.setBankId(getCurrentBankId());
            input.setTargetPricingComponentIds(componentIds);
            input.setActivePricingTierIds(activeTierIds); // Satisfies 'activePricingTierIds contains' in DRL
            input.setReferenceDate(request.getEffectiveDate());

            // CRITICAL: Set the top-level field so the DRL 'amountField' logic works
            input.setTransactionAmount(request.getTransactionAmount() != null ?
                    request.getTransactionAmount() : BigDecimal.ZERO);
            input.setCustomerSegment(request.getCustomerSegment());

            input.setRuleFired(false);

            // Populate Context Attributes for custom DRL expressions
            if (request.getCustomAttributes() != null) {
                input.getCustomAttributes().putAll(request.getCustomAttributes());
            }
            input.getCustomAttributes().put("productId", request.getProductId());
            input.getCustomAttributes().put("customerSegment", request.getCustomerSegment());
            input.getCustomAttributes().put("transactionAmount", input.getTransactionAmount());

            kieSession.insert(input);
            kieSession.fireAllRules();

            return kieSession.getObjects(new ClassObjectFilter(PriceValue.class))
                    .stream().map(PriceValue.class::cast).toList();
        } finally {
            kieSession.dispose();
        }
    }

    @Cacheable(value = "productPricingLinks",
            key = "T(com.bankengine.auth.security.TenantContextHolder).getBankId() + '_' + #productId + '_' + #effectiveDate")
    public List<ProductPricingLink> getCachedLinks(Long productId, LocalDate effectiveDate) {
        return productPricingLinkRepository.findByProductIdAndDate(productId, effectiveDate);
    }

    // -----------------------------------------------------------------------------------
    // MAPPING LOGIC
    // -----------------------------------------------------------------------------------

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
}