package com.bankengine.pricing.service;

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
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PricingCalculationService extends BaseService {

    private final KieContainerReloadService kieContainerReloadService;
    private final ProductPricingLinkRepository productPricingLinkRepository;
    private final PriceAggregator priceAggregator;

    private static final String CUSTOMER_SEGMENT_KEY = "customerSegment";
    private static final String TRANSACTION_AMOUNT_KEY = "transactionAmount";
    private static final String PRODUCT_ID_KEY = "productId";

    @Transactional(readOnly = true)
    public ProductPricingCalculationResult getProductPricing(PricingRequest request) {
        List<ProductPricingLink> links = productPricingLinkRepository.findByProductId(request.getProductId());

        if (links.isEmpty()) {
            throw new NotFoundException(String.format("No pricing components for Product ID %d", request.getProductId()));
        }

        List<PriceComponentDetail> allComponents = new ArrayList<>();
        BigDecimal txAmount = request.getAmount() != null ? request.getAmount() : BigDecimal.ZERO;

        // 1. Process Fixed Pricing
        links.stream()
                .filter(link -> !link.isUseRulesEngine())
                .map(link -> mapFixedLinkToDetail(link))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(allComponents::add);

        // 2. Process Rules Engine Pricing
        List<ProductPricingLink> ruleBasedLinks = links.stream()
                .filter(ProductPricingLink::isUseRulesEngine)
                .toList();

        if (!ruleBasedLinks.isEmpty()) {
            Set<Long> targetComponentIds = ruleBasedLinks.stream()
                    .map(link -> link.getPricingComponent().getId())
                    .collect(Collectors.toSet());

            Collection<PriceValue> ruleFacts = determinePriceWithDrools(request, targetComponentIds);
            ruleFacts.stream()
                    .map(fact -> mapFactToDetail(fact))
                    .forEach(allComponents::add);
        }

        // 3. Final Aggregation
        BigDecimal finalPrice = priceAggregator.calculate(allComponents, txAmount);

        return ProductPricingCalculationResult.builder()
                .finalChargeablePrice(finalPrice)
                .componentBreakdown(allComponents)
                .build();
    }

    private Collection<PriceValue> determinePriceWithDrools(PricingRequest request, Set<Long> targetIds) {
        KieSession kieSession = kieContainerReloadService.getKieContainer().newKieSession();

        PricingInput input = new PricingInput();
        input.setBankId(getCurrentBankId());
        input.setTargetPricingComponentIds(targetIds);

        // Flatten top-level DTO fields into the map for Drools LHS matching
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(PRODUCT_ID_KEY, request.getProductId());
        attrs.put(CUSTOMER_SEGMENT_KEY, request.getCustomerSegment());
        attrs.put(TRANSACTION_AMOUNT_KEY, request.getAmount());

        if (request.getCustomAttributes() != null) {
            attrs.putAll(request.getCustomAttributes());
        }
        input.setCustomAttributes(attrs);

        try {
            kieSession.insert(input);
            kieSession.fireAllRules();

            return kieSession.getObjects(new org.kie.api.runtime.ClassObjectFilter(PriceValue.class))
                    .stream()
                    .map(PriceValue.class::cast)
                    .collect(Collectors.toList());
        } finally {
            kieSession.dispose();
        }
    }

    private Optional<PriceComponentDetail> mapFixedLinkToDetail(ProductPricingLink link) {
        if (link.getFixedValue() == null || link.getFixedValueType() == null) {
            return Optional.empty();
        }

    BigDecimal calculatedAmount = calculateMonetaryValue(
            link.getFixedValue(),
            link.getFixedValueType()
    );

    return Optional.of(PriceComponentDetail.builder()
            .componentCode(link.getPricingComponent().getName())
            .rawValue(link.getFixedValue())
            .valueType(link.getFixedValueType())
            .calculatedAmount(calculatedAmount)
            .sourceType("FIXED_VALUE")
            .targetComponentCode(link.getTargetComponentCode())
            .build());
}

    private PriceComponentDetail mapFactToDetail(PriceValue fact) {
        BigDecimal calculatedAmount = calculateMonetaryValue(fact.getRawValue(), fact.getValueType());

        return PriceComponentDetail.builder()
                .componentCode(fact.getComponentCode())
                .rawValue(fact.getRawValue())
                .valueType(fact.getValueType())
                .calculatedAmount(calculatedAmount)
                .sourceType("RULES_ENGINE")
                .matchedTierId(fact.getMatchedTierId())
                .build();
    }

    /**
     * Common logic to convert a raw value (Fixed or Rule-based) into a monetary amount.
     */
    private BigDecimal calculateMonetaryValue(BigDecimal rawValue, PriceValue.ValueType type) {
        if (rawValue == null) return BigDecimal.ZERO;

        return switch (type) {
            // We only map Absolute values here.
            // Percentages stay 0 for now because the Aggregator will set them.
            case FEE_ABSOLUTE, DISCOUNT_ABSOLUTE -> rawValue;
            default -> BigDecimal.ZERO;
        };
    }
}