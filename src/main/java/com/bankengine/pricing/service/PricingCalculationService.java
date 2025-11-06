package com.bankengine.pricing.service;

import com.bankengine.pricing.model.PriceValue;
import com.bankengine.rules.model.PricingInput;
import com.bankengine.rules.service.KieContainerReloadService;
import com.bankengine.web.exception.NotFoundException;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class PricingCalculationService {

    private final KieContainerReloadService kieContainerReloadService;

    public PricingCalculationService(KieContainerReloadService kieContainerReloadService) {
        this.kieContainerReloadService = kieContainerReloadService;
    }

    @Transactional(readOnly = true)
    public PriceValue getCalculatedPrice(String customerSegment, BigDecimal transactionAmount) {
        // NOTE: The component parameter is primarily for ensuring the component exists
        // in the DB, as done in the controller. The rules run against ALL loaded components.

        // 1. Determine the price using the Rules Engine directly
        PricingInput finalInputFact = determinePriceWithDrools(customerSegment, transactionAmount);

        // 2. Extract the result
        if (finalInputFact.isRuleFired() && finalInputFact.getMatchedTierId() != null) {
            PriceValue resultValue = new PriceValue();
            resultValue.setPriceAmount(finalInputFact.getPriceAmount());
            resultValue.setCurrency(finalInputFact.getCurrency());
             resultValue.setValueType(PriceValue.ValueType.valueOf(finalInputFact.getValueType()));

            return resultValue;
        }

        // 3. Fallback for no match
        throw new NotFoundException("No matching pricing rule found for segment: " + customerSegment +
                                        " and amount: " + transactionAmount);
    }

    /**
     * Executes the Drools Rules Engine to find the final price.
     * The rules use the PricingInput fact and update its fields (priceAmount, valueType, currency).
     */
    private PricingInput determinePriceWithDrools(String customerSegment, BigDecimal transactionAmount) {

        // Get the active KieSession from the reloaded container
        KieSession kieSession = kieContainerReloadService.getKieContainer().newKieSession();

        // 1. Create the Input Fact
        PricingInput input = new PricingInput();
        input.setCustomerSegment(customerSegment);
        input.setTransactionAmount(transactionAmount);

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