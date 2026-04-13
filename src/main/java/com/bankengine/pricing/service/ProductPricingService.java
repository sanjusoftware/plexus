package com.bankengine.pricing.service;

import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.common.service.BaseService;
import com.bankengine.pricing.dto.ProductPriceRequest;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.dto.ProductPricingCalculationResult.PriceComponentDetail;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.ProductPricingLink;
import com.bankengine.pricing.repository.ProductPricingLinkRepository;
import com.bankengine.rules.model.PricingInput;
import com.bankengine.rules.service.KieContainerReloadService;
import com.bankengine.web.exception.NotFoundException;
import com.bankengine.web.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.ClassObjectFilter;
import org.kie.api.runtime.KieSession;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductPricingService extends BaseService {

    private static final String ATTR_TRANSACTION_AMOUNT = PricingAttributeKeys.TRANSACTION_AMOUNT;
    private static final String ATTR_EFFECTIVE_DATE = PricingAttributeKeys.EFFECTIVE_DATE;
    private static final String ATTR_PRODUCT_ID = PricingAttributeKeys.PRODUCT_ID;
    private static final String ATTR_BANK_ID = PricingAttributeKeys.BANK_ID;

    private final KieContainerReloadService kieContainerReloadService;
    private final ProductRepository productRepository;
    private final ProductPricingLinkRepository productPricingLinkRepository;
    private final PriceAggregator priceAggregator;

    private record PricingLinkContext(String targetComponentCode, LocalDate effectiveDate, LocalDate expiryDate) {}

    /**
     * Live Pricing Entry Point: Fetches configuration from DB/Rules and calculates final price.
     */
    @Transactional(readOnly = true)
    public ProductPricingCalculationResult getProductPricing(ProductPriceRequest request) {
        // 0. Validation & Security
        verifyProductAccess(request.getProductId());

        Map<String, Object> normalizedAttributes = buildNormalizedCustomAttributes(request);
        LocalDate effectiveDate = extractLocalDate(normalizedAttributes.get(ATTR_EFFECTIVE_DATE), LocalDate.now());

        // 1. Data Retrieval: Fetch active pricing links (Fixed and Rule-based definitions)
        List<ProductPricingLink> activePricingLinks = getActivePricingLinks(request.getProductId(), effectiveDate);

        // 2. Component Assembly: Combine Static (DB) and Dynamic (Drools) components
        List<PriceComponentDetail> priceComponentDetails = assemblePricingComponents(activePricingLinks, normalizedAttributes);

        // 3. Calculation: Delegate to the Pure Calculation Engine (PriceAggregator)
        // TRANSACTION_AMOUNT is the canonical base for percentage fee math.
        BigDecimal productFeeCalculationBaseAmount = extractBigDecimal(normalizedAttributes.get(ATTR_TRANSACTION_AMOUNT), BigDecimal.ZERO);

        // Note: For a single product, the 'netImpact' is the sum of its fees and discounts
        BigDecimal netImpact = priceAggregator.calculateBundleImpact(
                priceComponentDetails,
                productFeeCalculationBaseAmount,
                BigDecimal.ZERO,
                request.getEnrollmentDate(),
                effectiveDate);

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

    private List<ProductPricingLink> getActivePricingLinks(Long productId, LocalDate effectiveDate) {
        List<ProductPricingLink> links = getCachedLinks(productId, effectiveDate);
        if (links.isEmpty()) {
            throw new NotFoundException("No active pricing configuration found for product: "
                    + productId + " on date: " + effectiveDate);
        }
        return links;
    }

    /**
     * Gathers all definitions (Fixed from DB and Tiers from Rules).
     */
    private List<PriceComponentDetail> assemblePricingComponents(List<ProductPricingLink> links,
                                                                 Map<String, Object> normalizedAttributes) {
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
            components.addAll(retrieveDynamicComponents(ruleBasedLinks, normalizedAttributes));
        }

        return components;
    }

    private List<PriceComponentDetail> retrieveDynamicComponents(List<ProductPricingLink> ruleLinks,
                                                                 Map<String, Object> normalizedAttributes) {
        // 1. Identify Target Component Codes and Versions
        Set<String> componentCodes = ruleLinks.stream()
                .map(l -> l.getPricingComponent().getCode() + ":" + l.getPricingComponent().getVersion())
                .collect(Collectors.toSet());

        // 2. Harvest Tier Codes directly from the active component links.
        Set<String> activeTierCodes = ruleLinks.stream()
                .flatMap(l -> l.getPricingComponent().getPricingTiers().stream())
                .map(PricingTier::getCode)
                .collect(Collectors.toSet());

        // Preserve optional target mapping for rule-based discounts.
        Map<String, PricingLinkContext> linkContextByComponentCode = new HashMap<>();
        ruleLinks.forEach(link -> {
            String componentCode = link.getPricingComponent().getCode();
            linkContextByComponentCode.put(componentCode, new PricingLinkContext(
                    link.getTargetComponentCode(),
                    link.getEffectiveDate(),
                    link.getExpiryDate()
            ));
        });

        // 3. Execute Rules with fully populated code sets
        return determinePriceWithDrools(componentCodes, activeTierCodes, normalizedAttributes).stream()
                .map(fact -> mapFactToDetail(fact, linkContextByComponentCode.get(fact.getComponentCode())))
                .toList();
    }

    private Collection<PriceValue> determinePriceWithDrools(Set<String> componentCodes,
                                                            Set<String> activeTierCodes,
                                                            Map<String, Object> normalizedAttributes) {
        KieSession kieSession = kieContainerReloadService.getKieContainer().newKieSession();
        try {
            PricingInput input = new PricingInput();
            input.setBankId(getCurrentBankId());
            input.setTargetPricingComponentCodes(componentCodes);
            input.setActivePricingTierCodes(activeTierCodes);

            input.setRuleFired(false);

            input.getCustomAttributes().putAll(normalizedAttributes);

            kieSession.setGlobal("log", log);
            kieSession.insert(input);
            kieSession.fireAllRules();

            return kieSession.getObjects(new ClassObjectFilter(PriceValue.class))
                    .stream().map(PriceValue.class::cast).toList();
        } finally {
            kieSession.dispose();
        }
    }

    private Map<String, Object> buildNormalizedCustomAttributes(ProductPriceRequest request) {
        Map<String, Object> attributes = new HashMap<>();
        if (request.getCustomAttributes() != null) {
            rejectLegacySystemAliases(request.getCustomAttributes());
            attributes.putAll(request.getCustomAttributes());
        }

        attributes.putIfAbsent(ATTR_TRANSACTION_AMOUNT, BigDecimal.ZERO);
        attributes.putIfAbsent(ATTR_EFFECTIVE_DATE, LocalDate.now());
        attributes.putIfAbsent(ATTR_PRODUCT_ID, request.getProductId());
        attributes.putIfAbsent(ATTR_BANK_ID, getCurrentBankId());
        return attributes;
    }

    private void rejectLegacySystemAliases(Map<String, Object> incomingAttributes) {
        List<String> legacyKeys = incomingAttributes.keySet().stream()
                .filter(PricingAttributeKeys.LEGACY_ALIASES::contains)
                .sorted()
                .toList();
        if (!legacyKeys.isEmpty()) {
            throw new ValidationException("Legacy pricing attribute keys are not supported: "
                    + String.join(", ", legacyKeys)
                    + ". Use canonical keys: "
                    + String.join(", ", PricingAttributeKeys.SYSTEM_KEYS));
        }
    }


    private BigDecimal extractBigDecimal(Object value, BigDecimal defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private LocalDate extractLocalDate(Object value, LocalDate defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof LocalDate date) return date;
        try {
            return LocalDate.parse(value.toString());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    @Cacheable(value = "productPricingLinks",
            key = "T(com.bankengine.auth.security.TenantContextHolder).getBankId() + '_' + #productId + '_' + #effectiveDate")
    public List<ProductPricingLink> getCachedLinks(Long productId, LocalDate effectiveDate) {
        LocalDate cycleReference = effectiveDate != null ? effectiveDate : LocalDate.now();
        LocalDate cycleStart = cycleReference.withDayOfMonth(1);
        LocalDate cycleEnd = cycleReference.withDayOfMonth(cycleReference.lengthOfMonth());
        return productPricingLinkRepository.findByProductIdOverlappingCycle(productId, cycleStart, cycleEnd);
    }

    // -----------------------------------------------------------------------------------
    // MAPPING LOGIC
    // -----------------------------------------------------------------------------------

    private PriceComponentDetail mapFixedLinkToDetail(ProductPricingLink link) {
        return PriceComponentDetail.builder()
                .componentCode(link.getPricingComponent().getCode())
                .rawValue(link.getFixedValue())
                .valueType(link.getFixedValueType())
                .sourceType("FIXED_VALUE")
                .targetComponentCode(link.getTargetComponentCode())
                .proRataApplicable(link.getPricingComponent().isProRataApplicable())
                .applyChargeOnFullBreach(false)
                .effectiveDate(link.getEffectiveDate())
                .expiryDate(link.getExpiryDate())
                .build();
    }

    private PriceComponentDetail mapFactToDetail(PriceValue fact, PricingLinkContext linkContext) {
        boolean proRata = false;
        boolean fullBreach = false;
        String tierCode = fact.getMatchedTierCode();

        if (fact.getPricingTier() != null) {
            proRata = fact.getPricingTier().getPricingComponent().isProRataApplicable();
            fullBreach = fact.getPricingTier().isApplyChargeOnFullBreach();
            if (tierCode == null) {
                tierCode = fact.getPricingTier().getCode();
            }
        }

        return PriceComponentDetail.builder()
                .componentCode(fact.getComponentCode())
                .rawValue(fact.getRawValue())
                .valueType(fact.getValueType())
                .sourceType("RULES_ENGINE")
                .targetComponentCode(linkContext != null ? linkContext.targetComponentCode() : null)
                .matchedTierId(fact.getMatchedTierId())
                .matchedTierCode(tierCode)
                .proRataApplicable(proRata)
                .applyChargeOnFullBreach(fullBreach)
                .effectiveDate(linkContext != null ? linkContext.effectiveDate() : null)
                .expiryDate(linkContext != null ? linkContext.expiryDate() : null)
                .build();
    }
}