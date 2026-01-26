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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

        // 2. FETCH LINKS
        List<ProductPricingLink> links = productPricingLinkRepository.findByProductId(request.getProductId());

        if (links.isEmpty()) {
            throw new NotFoundException("No pricing configuration found for product ID: " + request.getProductId());
        }

        List<PriceComponentDetail> allComponents = new ArrayList<>();
        BigDecimal txAmount = request.getAmount() != null ? request.getAmount() : BigDecimal.ZERO;

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

        // 5. Aggregate
        BigDecimal finalPrice = priceAggregator.calculate(allComponents, txAmount);

        return ProductPricingCalculationResult.builder()
                .finalChargeablePrice(finalPrice)
                .componentBreakdown(allComponents)
                .build();
    }

    private PriceComponentDetail mapFixedLinkToDetail(ProductPricingLink link) {
        BigDecimal signedAmount = calculateSignedAmount(link.getFixedValue(), link.getFixedValueType());
        return PriceComponentDetail.builder()
                .componentCode(link.getPricingComponent().getName())
                .rawValue(link.getFixedValue())
                .valueType(link.getFixedValueType())
                .calculatedAmount(signedAmount)
                .sourceType("FIXED_VALUE")
                .targetComponentCode(link.getTargetComponentCode())
                .build();
    }

    private PriceComponentDetail mapFactToDetail(PriceValue fact) {
        BigDecimal signedAmount = calculateSignedAmount(fact.getRawValue(), fact.getValueType());
        return PriceComponentDetail.builder()
                .componentCode(fact.getComponentCode())
                .rawValue(fact.getRawValue())
                .valueType(fact.getValueType())
                .calculatedAmount(signedAmount)
                .sourceType("RULES_ENGINE")
                .matchedTierId(fact.getMatchedTierId())
                .build();
    }

    private BigDecimal calculateSignedAmount(BigDecimal rawValue, PriceValue.ValueType type) {
        if (rawValue == null) return BigDecimal.ZERO;
        BigDecimal absVal = rawValue.abs();

        // At product level, we keep percentages as 0 for Aggregator to handle,
        // but ensure absolute values have correct signs.
        return switch (type) {
            case FEE_ABSOLUTE -> absVal;
            case DISCOUNT_ABSOLUTE -> absVal.negate();
            default -> BigDecimal.ZERO;
        };
    }

    private Collection<PriceValue> determinePriceWithDrools(PricingRequest request, Set<Long> targetIds) {
        KieSession kieSession = kieContainerReloadService.getKieContainer().newKieSession();
        PricingInput input = new PricingInput();
        input.setBankId(getCurrentBankId());
        input.setTargetPricingComponentIds(targetIds);

        if (request.getCustomAttributes() != null) {
            input.getCustomAttributes().putAll(request.getCustomAttributes());
        }

        // Override/set standard fields to ensure they use the "Official" DTO values
        // This acts as a "Source of Truth" guarantee.
        input.getCustomAttributes().put("productId", request.getProductId());
        input.getCustomAttributes().put("customerSegment", request.getCustomerSegment());
        input.getCustomAttributes().put("transactionAmount", request.getAmount());

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