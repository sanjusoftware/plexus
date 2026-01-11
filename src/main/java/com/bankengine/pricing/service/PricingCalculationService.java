package com.bankengine.pricing.service;

import com.bankengine.common.service.BaseService;
import com.bankengine.pricing.dto.PriceRequest;
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
    private static final String BANK_ID_KEY = "bankId";

    @Transactional(readOnly = true)
    public ProductPricingCalculationResult getProductPricing(PriceRequest request) {
        List<ProductPricingLink> links = productPricingLinkRepository.findByProductId(request.getProductId());

        if (links.isEmpty()) {
            throw new NotFoundException(String.format("No pricing components for Product ID %d", request.getProductId()));
        }

        List<PriceComponentDetail> allComponents = new ArrayList<>();

        // 1. Collect Fixed Pricing
        links.stream()
                .filter(link -> !link.isUseRulesEngine())
                .map(this::mapFixedLinkToDetail)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(allComponents::add);

        // 2. Collect Rules Engine Pricing
        boolean hasRules = links.stream().anyMatch(ProductPricingLink::isUseRulesEngine);
        if (hasRules) {
            Collection<PriceValue> ruleFacts = determinePriceWithDrools(request);
            ruleFacts.stream()
                    .map(this::mapFactToDetail)
                    .forEach(allComponents::add);
        }

        // 3. Aggregate for the Final Price
        BigDecimal finalPrice = priceAggregator.calculate(allComponents, request.getAmount());

        return ProductPricingCalculationResult.builder()
                .finalChargeablePrice(finalPrice)
                .componentBreakdown(allComponents)
                .build();
    }

    private Collection<PriceValue> determinePriceWithDrools(PriceRequest request) {
        KieSession kieSession = kieContainerReloadService.getKieContainer().newKieSession();

        PricingInput input = new PricingInput();
        input.setBankId(getCurrentBankId());
        input.setCustomAttributes(new HashMap<>());
        input.getCustomAttributes().put(PRODUCT_ID_KEY, request.getProductId());
        input.getCustomAttributes().put(CUSTOMER_SEGMENT_KEY, request.getCustomerSegment());
        input.getCustomAttributes().put(TRANSACTION_AMOUNT_KEY, request.getAmount());

        if (request.getCustomAttributes() != null) {
            input.getCustomAttributes().putAll(request.getCustomAttributes());
        }

        try {
            kieSession.insert(input);
            kieSession.fireAllRules();

            return kieSession.getObjects(new org.kie.api.runtime.ClassObjectFilter(PriceValue.class))
                    .stream()
                    .filter(PriceValue.class::isInstance)
                    .map(PriceValue.class::cast)
                    .collect(Collectors.toList());
        } finally {
            kieSession.dispose();
        }
    }

    private Optional<PriceComponentDetail> mapFixedLinkToDetail(ProductPricingLink link) {
        if (link.getFixedValue() == null) return Optional.empty();

        return Optional.of(PriceComponentDetail.builder()
                .context(link.getContext())
                .componentCode(link.getPricingComponent().getName())
                .amount(link.getFixedValue())
                .valueType(PriceValue.ValueType.ABSOLUTE)
                .sourceType("FIXED_VALUE")
                .build());
    }

    private PriceComponentDetail mapFactToDetail(PriceValue fact) {
        return PriceComponentDetail.builder()
                .context(fact.getComponentCode())
                .componentCode(fact.getComponentCode())
                .amount(fact.getPriceAmount())
                .valueType(fact.getValueType())
                .sourceType("RULES_ENGINE")
                .matchedTierId(fact.getMatchedTierId())
                .build();
    }
}