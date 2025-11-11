package com.bankengine.pricing.service;

import com.bankengine.pricing.model.PriceValue;
import com.bankengine.rules.model.PricingInput;
import com.bankengine.rules.service.KieContainerReloadService;
import com.bankengine.web.exception.NotFoundException;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;

@Service
public class PricingCalculationService {

    private final KieContainerReloadService kieContainerReloadService;

    // Define constant keys used by the service to populate the map
    private static final String CUSTOMER_SEGMENT_KEY = "customerSegment";
    private static final String TRANSACTION_AMOUNT_KEY = "transactionAmount";

    public PricingCalculationService(KieContainerReloadService kieContainerReloadService) {
        this.kieContainerReloadService = kieContainerReloadService;
    }

    @Transactional(readOnly = true)
    public PriceValue getCalculatedPrice(String customerSegment, BigDecimal transactionAmount) {
        // NOTE: Error handling message is simplified as not all inputs are used in the message.

        // 1. Determine the price using the Rules Engine directly
        PricingInput finalInputFact = determinePriceWithDrools(customerSegment, transactionAmount);

        // 2. Extract the result
        if (finalInputFact.isRuleFired() && finalInputFact.getMatchedTierId() != null) {
            PriceValue resultValue = new PriceValue();
            resultValue.setPriceAmount(finalInputFact.getPriceAmount());
            resultValue.setCurrency(finalInputFact.getCurrency());
             // Ensure PriceValue.ValueType can be created from the String
             resultValue.setValueType(PriceValue.ValueType.valueOf(finalInputFact.getValueType()));

            return resultValue;
        }

        // 3. Fallback for no match
        throw new NotFoundException("No matching pricing rule found for the given criteria.");
    }

    /**
     * Executes the Drools Rules Engine to find the final price.
     * The rules use the PricingInput fact and update its output fields.
     */
    private PricingInput determinePriceWithDrools(String customerSegment, BigDecimal transactionAmount) {

        // Get the active KieSession from the reloaded container
        KieSession kieSession = kieContainerReloadService.getKieContainer().newKieSession();

        // 1. Create the Input Fact
        PricingInput input = new PricingInput();

        // Use the customAttributes map to pass inputs ---
        input.setCustomAttributes(new HashMap<>());

        // Populate the map using the defined keys
        if (customerSegment != null) {
            input.getCustomAttributes().put(CUSTOMER_SEGMENT_KEY, customerSegment);
        }
        if (transactionAmount != null) {
            input.getCustomAttributes().put(TRANSACTION_AMOUNT_KEY, transactionAmount);
        }
        // If there were other inputs (e.g., productCode, isNewCustomer), they would be added here too.

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