package com.bankengine.pricing.service;

import com.bankengine.auth.security.BankContextHolder;
import com.bankengine.pricing.dto.PriceRequest;
import com.bankengine.pricing.dto.PriceValueResponseDto;
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

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PricingCalculationService {

    private final KieContainerReloadService kieContainerReloadService;
    private final ProductPricingLinkRepository productPricingLinkRepository;

    private static final String CUSTOMER_SEGMENT_KEY = "customerSegment";
    private static final String TRANSACTION_AMOUNT_KEY = "transactionAmount";
    private static final String PRODUCT_ID_KEY = "productId";
    private static final String BANK_ID_KEY = "bankId";

    /**
     * Calculates the final price for a product, handling both simple (fixed) and complex (rules-based) pricing components.
     * @param request The price calculation input criteria.
     * @return A list of prices (PriceValueResponseDto) for all linked pricing components.
     */
    @Transactional(readOnly = true)
    public List<PriceValueResponseDto> getProductPricing(PriceRequest request) {

        String bankId = BankContextHolder.getBankId();
        List<ProductPricingLink> links = productPricingLinkRepository.findByProductId(request.getProductId());

        if (links.isEmpty()) {
            throw new NotFoundException(String.format("No pricing components linked to Product ID %d for bank %s.",
                request.getProductId(), bankId));
        }

        // --- 1. Separate Links: Fixed vs. Rules Engine ---
        List<ProductPricingLink> fixedLinks = links.stream().filter(link -> !link.isUseRulesEngine()).toList();
        List<ProductPricingLink> rulesLinks = links.stream().filter(ProductPricingLink::isUseRulesEngine).toList();

        List<PriceValueResponseDto> results = new ArrayList<>();

        // 2. Process Fixed Pricing (Existing Logic)
        results.addAll(fixedLinks.stream()
            .map(this::processFixedPricingComponent)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList());

        // 3. Process Rules Engine Pricing
        if (!rulesLinks.isEmpty()) {
             Collection<PriceValue> ruleFacts = determinePriceWithDrools(request, bankId);
             results.addAll(
                 ruleFacts.stream()
                     .map(this::mapPriceValueToDto)
                     .toList()
             );
        }

        // TODO: Future enhancement: Implement aggregation logic here to return a single final chargeable price.

        return results;
    }

    /**
     * Helper to process fixed pricing components (extracted from original processPricingComponent).
     */
    private Optional<PriceValueResponseDto> processFixedPricingComponent(ProductPricingLink link) {
        if (link.getFixedValue() != null) {
            return Optional.of(PriceValueResponseDto.builder()
                .context(link.getContext())
                .pricingComponentCode(link.getPricingComponent().getName())
                .priceAmount(link.getFixedValue())
                .valueType(PriceValue.ValueType.ABSOLUTE)
                .sourceType("FIXED_VALUE")
                .build());
        }
        return Optional.empty();
    }

    /**
     * Executes the Drools Rules Engine, populating the input fact and collecting all inserted PriceValue facts.
     * * @return Collection of PriceValue facts inserted by the rules.
     */
    private Collection<PriceValue> determinePriceWithDrools(PriceRequest request, String bankId) {

        KieSession kieSession = kieContainerReloadService.getKieContainer().newKieSession();

        PricingInput input = new PricingInput();
        input.setCustomAttributes(new HashMap<>());

        // Populate the map using the required keys for the DRL rules
        input.getCustomAttributes().put(PRODUCT_ID_KEY, request.getProductId());
        input.getCustomAttributes().put(CUSTOMER_SEGMENT_KEY, request.getCustomerSegment());
        input.getCustomAttributes().put(TRANSACTION_AMOUNT_KEY, request.getAmount());
        input.getCustomAttributes().put(BANK_ID_KEY, bankId);

        if (request.getCustomAttributes() != null) {
            input.getCustomAttributes().putAll(request.getCustomAttributes());
        }

        try {
            kieSession.insert(input);
            kieSession.fireAllRules();

            // Collect all inserted PriceValue facts from the working memory.
            Collection<PriceValue> insertedObjects = (Collection<PriceValue>) kieSession.getObjects(
                    new org.kie.api.runtime.ClassObjectFilter(PriceValue.class)
            );

            Collection<PriceValue> safeCopy = insertedObjects.stream()
                    .filter(PriceValue.class::isInstance)
                    .collect(Collectors.toList());

            return safeCopy;

        } finally {
            kieSession.dispose();
        }
    }

    /**
     * Maps the runtime PriceValue fact to the response DTO.
     */
    private PriceValueResponseDto mapPriceValueToDto(PriceValue fact) {
        String context = fact.getComponentCode() != null ? fact.getComponentCode() : "RULES_ENGINE";

        return PriceValueResponseDto.builder()
            .context(context)
            .pricingComponentCode(fact.getComponentCode())
            .priceAmount(fact.getPriceAmount())
            .valueType(fact.getValueType())
            .sourceType("RULES_ENGINE")
            .matchedTierId(fact.getMatchedTierId())
            .build();
    }
}