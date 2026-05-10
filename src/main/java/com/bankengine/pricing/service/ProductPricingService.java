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

    private record PricingLinkContext(String targetComponentCode, LocalDate effectiveDate, LocalDate expiryDate) {
    }

    @Transactional(readOnly = true)
    public ProductPricingCalculationResult getProductPricing(ProductPriceRequest request) {
        if (request.getCustomAttributes() == null) {
            throw new ValidationException("Custom attributes map must not be null (it can be empty).");
        }
        verifyProductAccess(request.getProductId());

        Map<String, Object> normalizedAttributes = buildNormalizedCustomAttributes(request);
        LocalDate requestedDate = extractLocalDate(normalizedAttributes.get(ATTR_EFFECTIVE_DATE), LocalDate.now());

        List<ProductPricingLink> linksInCycle = getLinksByCycle(request.getProductId(), requestedDate);
        if (linksInCycle.isEmpty()) {
            throw new NotFoundException("No active pricing configuration found for product: " + request.getProductId());
        }

        List<PriceComponentDetail> priceComponentDetails = assemblePricingComponents(linksInCycle, requestedDate, normalizedAttributes);

        BigDecimal transactionAmount = extractBigDecimal(normalizedAttributes.get(ATTR_TRANSACTION_AMOUNT), BigDecimal.ZERO);
        BigDecimal netImpact = priceAggregator.calculateBundleImpact(
                priceComponentDetails,
                transactionAmount,
                BigDecimal.ZERO,
                request.getEnrollmentDate(),
                requestedDate);

        return ProductPricingCalculationResult.builder()
                .finalChargeablePrice(netImpact)
                .componentBreakdown(priceComponentDetails)
                .build();
    }

    private void verifyProductAccess(Long productId) {
        getByIdSecurely(productRepository, productId, "Product");
    }

    private List<ProductPricingLink> getLinksByCycle(Long productId, LocalDate requestedDate) {
        LocalDate cycleStart = requestedDate.withDayOfMonth(1);
        LocalDate cycleEnd = requestedDate.withDayOfMonth(requestedDate.lengthOfMonth());
        return productPricingLinkRepository.findByProductIdOverlappingCycle(productId, cycleStart, cycleEnd);
    }

    private List<PriceComponentDetail> assemblePricingComponents(List<ProductPricingLink> links,
                                                                 LocalDate requestedDate,
                                                                 Map<String, Object> normalizedAttributes) {
        List<ProductPricingLink> eligibleLinks = links.stream()
                .filter(l -> isLinkEligibleForDate(l, requestedDate))
                .toList();

        List<PriceComponentDetail> components = new ArrayList<>();

        // Fixed Links
        eligibleLinks.stream()
                .filter(link -> !link.isUseRulesEngine() && link.getFixedValue() != null)
                .map(this::mapFixedLinkToDetail)
                .forEach(components::add);

        // Rules Engine Links
        List<ProductPricingLink> ruleBasedLinks = eligibleLinks.stream()
                .filter(ProductPricingLink::isUseRulesEngine)
                .toList();

        if (!ruleBasedLinks.isEmpty()) {
            components.addAll(retrieveDynamicComponents(ruleBasedLinks, requestedDate, normalizedAttributes));
        }

        return components;
    }

    private boolean isLinkEligibleForDate(ProductPricingLink link, LocalDate requestedDate) {
        if (link.getEffectiveDate() == null) {
            log.warn("Pricing link {} ignored: Missing Effective Date", link.getId());
            return false;
        }

        boolean isActiveNow = !link.getEffectiveDate().isAfter(requestedDate)
                && (link.getExpiryDate() == null || !link.getExpiryDate().isBefore(requestedDate));

        if (isActiveNow) {
            return true;
        }

        boolean proRata = link.getPricingComponent().isProRataApplicable();
        boolean fullBreach = link.getPricingComponent().getPricingTiers().stream()
                .anyMatch(PricingTier::isApplyChargeOnFullBreach);

        return proRata || fullBreach;
    }

    private List<PriceComponentDetail> retrieveDynamicComponents(List<ProductPricingLink> ruleLinks,
                                                                 LocalDate requestedDate,
                                                                 Map<String, Object> normalizedAttributes) {
        Set<String> componentCodes = ruleLinks.stream()
                .map(l -> l.getPricingComponent().getCode() + ":" + l.getPricingComponent().getVersion())
                .collect(Collectors.toSet());

        Set<String> activeTierCodes = ruleLinks.stream()
                .flatMap(l -> l.getPricingComponent().getPricingTiers().stream())
                .map(PricingTier::getCode)
                .collect(Collectors.toSet());

        Map<String, PricingLinkContext> linkContextByComponentCode = ruleLinks.stream()
                .collect(Collectors.toMap(
                        l -> l.getPricingComponent().getCode(),
                        l -> new PricingLinkContext(l.getTargetComponentCode(), l.getEffectiveDate(), l.getExpiryDate()),
                        (existing, replacement) -> existing
                ));

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
            attributes.putAll(request.getCustomAttributes());
        }
        attributes.putIfAbsent(ATTR_TRANSACTION_AMOUNT, BigDecimal.ZERO);
        attributes.putIfAbsent(ATTR_EFFECTIVE_DATE, LocalDate.now());
        attributes.putIfAbsent(ATTR_PRODUCT_ID, request.getProductId());
        attributes.putIfAbsent(ATTR_BANK_ID, getCurrentBankId());
        return attributes;
    }

    private BigDecimal extractBigDecimal(Object value, BigDecimal defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (Exception ex) {
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

    private PriceComponentDetail mapFactToDetail(PriceValue fact, PricingLinkContext context) {
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
                .targetComponentCode(context != null ? context.targetComponentCode() : null)
                .matchedTierId(fact.getMatchedTierId())
                .matchedTierCode(tierCode)
                .proRataApplicable(proRata)
                .applyChargeOnFullBreach(fullBreach)
                .effectiveDate(context != null ? context.effectiveDate() : null)
                .expiryDate(context != null ? context.expiryDate() : null)
                .build();
    }
}