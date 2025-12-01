package com.bankengine.rules.service;

import com.bankengine.rules.model.BundlePricingInput;
import lombok.RequiredArgsConstructor;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BundleRulesEngineService {

    private final KieContainerReloadService kieContainerReloadService;

    /**
     * Executes the dedicated Bundle Rules Engine to find adjustments (waivers, discounts).
     */
    public BundlePricingInput determineBundleAdjustments(BundlePricingInput inputFact) {

        // Note: In a real system, you would load a *different* KieContainer/KieSession here
        // dedicated only to "BUNDLE_ADJUSTMENT" rules. For simplicity, we reuse the reloader.
        KieSession kieSession = kieContainerReloadService.getKieContainer().newKieSession();

        try {
            // 1. Insert the input fact
            kieSession.insert(inputFact);

            // 2. Fire all bundle-specific rules
            kieSession.fireAllRules();

            // The input object is updated by the rules
            return inputFact;

        } finally {
            kieSession.dispose();
        }
    }
}