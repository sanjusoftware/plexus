package com.bankengine.pricing.service;

import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.rules.dto.PricingRuleInput;
// Drools Imports
import com.bankengine.web.exception.NotFoundException;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class PricingCalculationService {

    private static final Logger logger = LoggerFactory.getLogger(PricingCalculationService.class);
    private final PricingTierService pricingTierService;
    private final KieContainer kieContainer;

    public PricingCalculationService(PricingTierService pricingTierService, KieContainer kieContainer) {
        this.pricingTierService = pricingTierService;
        this.kieContainer = kieContainer;
    }

    @Transactional(readOnly = true)
    public PriceValue getCalculatedPrice(String customerSegment, BigDecimal transactionAmount, PricingComponent component) {

        // 1. Fetch all relevant Pricing Tiers for the component
        List<PricingTier> availableTiers = pricingTierService.findAllByPricingComponent(component);

        // 2. Determine the correct Tier using the Rules Engine
        Optional<PricingTier> matchedTier = determineTierWithDrools(customerSegment, transactionAmount, availableTiers);

        // 3. Find the corresponding PriceValue within the matched Tier (unchanged logic)
        if (matchedTier.isPresent()) {
            return findMatchingPriceValue(matchedTier.get());
        }

        // 4. Fallback for no match: RULE FAILURE (404 NOT FOUND via GlobalExceptionHandler)
        // This means, for the given input criteria, no price exists.
        throw new IllegalStateException("No matching pricing tier found for segment: " + customerSegment +
                                        " and amount: " + transactionAmount);
    }

    /**
     * Executes the Drools Rules Engine to find the correct Pricing Tier ID.
     */
    private Optional<PricingTier> determineTierWithDrools(String customerSegment, BigDecimal transactionAmount, List<PricingTier> availableTiers) {
        // Create a new session for each execution to ensure thread safety and clean working memory
        KieSession kieSession = kieContainer.newKieSession();

        try {
            // Set the logger global for DRL logging
            kieSession.setGlobal("logger", logger);

            // 1. Create the Input Fact
            PricingRuleInput input = new PricingRuleInput();
            input.setCustomerSegment(customerSegment);
            input.setTransactionAmount(transactionAmount);
            input.setAvailableTiers(availableTiers);

            // 2. Insert the fact into the rules engine's working memory
            kieSession.insert(input);

            // 3. Fire all matching rules
            kieSession.fireAllRules();

            // 4. Extract the result
            if (input.isRuleFired() && input.getMatchedTierId() != null) {
                Long matchedTierId = input.getMatchedTierId();
                return availableTiers.stream()
                        .filter(t -> t.getId().equals(matchedTierId))
                        .findFirst();
            }

            return Optional.empty(); // No rule fired

        } finally {
            // CRITICAL: Always dispose of the session to clean up resources!
            kieSession.dispose();
        }
    }

    /**
     * Internal method to find the specific PriceValue within the Tier.
     */
    private PriceValue findMatchingPriceValue(PricingTier pricingTier) {
        // For now, let's assume each Tier only has one PriceValue linked to it
        // This is where more complex logic (like looking for a specific PriceValue by date/region)
        // would go, but we keep it simple for now.
        if (pricingTier.getPriceValues() != null && !pricingTier.getPriceValues().isEmpty()) {
            return pricingTier.getPriceValues().iterator().next();
        }

        throw new IllegalArgumentException("Pricing Tier ID " + pricingTier.getId() +
                " is matched by rules but contains no PriceValues. Check configuration.");
    }
}