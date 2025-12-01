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

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PricingCalculationService {

    private final KieContainerReloadService kieContainerReloadService;
    private final ProductPricingLinkRepository productPricingLinkRepository;

    // Drools fact keys
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

        // 1. Get current tenant ID securely
        String bankId = BankContextHolder.getBankId();

        // 2. Fetch all applicable pricing links for the Product ID (automatically filtered by Bank ID via Hibernate)
        List<ProductPricingLink> links = productPricingLinkRepository.findByProductId(request.getProductId());

        if (links.isEmpty()) {
            throw new NotFoundException(String.format("No pricing components linked to Product ID %d for bank %s.",
                request.getProductId(), bankId));
        }

        // 3. Process all components and collect results, mapping PriceValue to the Response DTO
        return links.stream()
            .map(link -> processPricingComponent(link, request, bankId))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    }

    /**
     * Determines the price for a single ProductPricingLink based on its configuration.
     */
    private Optional<PriceValueResponseDto> processPricingComponent(
        ProductPricingLink link,
        PriceRequest request,
        String bankId) {

        // Use the Builder to construct the final response DTO
        PriceValueResponseDto.PriceValueResponseDtoBuilder resultBuilder = PriceValueResponseDto.builder()
            .context(link.getContext())
            .pricingComponentCode(link.getPricingComponent().getName())
            .currency("USD"); // Assuming default currency for now

        // --- 1. Simple Pricing: Use the fixed value stored on the link entity ---
        if (!link.isUseRulesEngine()) {
            if (link.getFixedValue() != null) {
                return Optional.of(resultBuilder
                    .priceAmount(link.getFixedValue())
                    .valueType(PriceValue.ValueType.ABSOLUTE) // Assume absolute for fixed fees
                    .sourceType("FIXED_VALUE")
                    .build());
            }
            return Optional.empty();
        }

        // --- 2. Complex Pricing: Use Drools Rules Engine ---

        PricingInput finalInputFact = determinePriceWithDrools(request, bankId);

        // 3. Extract the result
        if (finalInputFact.isRuleFired() && finalInputFact.getMatchedTierId() != null) {

            return Optional.of(resultBuilder
                .priceAmount(finalInputFact.getPriceAmount())
                .valueType(PriceValue.ValueType.valueOf(finalInputFact.getValueType()))
                .currency(finalInputFact.getCurrency())
                .sourceType("RULES_ENGINE")
                .build());
        }

        // 4. Fallback for no match
        return Optional.empty();
    }


    /**
     * Executes the Drools Rules Engine, populating the input fact with catalog and context data.
     */
    private PricingInput determinePriceWithDrools(PriceRequest request, String bankId) {

        // Get the active KieSession from the reloaded container
        KieSession kieSession = kieContainerReloadService.getKieContainer().newKieSession();

        // 1. Create the Input Fact
        PricingInput input = new PricingInput();
        input.setCustomAttributes(new HashMap<>());

        // Populate the map using the required keys for the DRL rules
        input.getCustomAttributes().put(PRODUCT_ID_KEY, request.getProductId().toString());
        input.getCustomAttributes().put(CUSTOMER_SEGMENT_KEY, request.getCustomerSegment());
        input.getCustomAttributes().put(TRANSACTION_AMOUNT_KEY, request.getAmount());
        input.getCustomAttributes().put(BANK_ID_KEY, bankId);

        try {
            // 2. Insert the input fact into the working memory
            kieSession.insert(input);

            // 3. Fire all matching rules
            kieSession.fireAllRules();

            // The input object is updated by the 'update($input)' action in the DRL.
            return input;

        } finally {
            // Always dispose of the stateful KieSession after use
            kieSession.dispose();
        }
    }
}