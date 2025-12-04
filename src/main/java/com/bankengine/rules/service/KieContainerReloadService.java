package com.bankengine.rules.service;

import com.bankengine.auth.security.BankContextHolder;
import com.bankengine.config.drools.DroolsKieModuleBuilder;
import com.bankengine.pricing.service.BundleDroolsRuleBuilderService;
import com.bankengine.pricing.service.DroolsRuleBuilderService;
import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class KieContainerReloadService {

    // Holds the currently active KieContainer instance
    private final AtomicReference<KieContainer> activeKieContainer;

    // CRITICAL FIX: Inject the Spring proxy of this service into itself
    // to enable @Transactional processing on self-invoked methods.
    @Autowired
    @Lazy
    private KieContainerReloadService self;

    private final DroolsRuleBuilderService ruleBuilderService;
    private final BundleDroolsRuleBuilderService bundleRuleBuilderService;
    private final DroolsKieModuleBuilder moduleBuilder;

    @Autowired
    public KieContainerReloadService(KieContainer initialKieContainer,
                                     DroolsRuleBuilderService ruleBuilderService,
                                     BundleDroolsRuleBuilderService bundleRuleBuilderService,
                                     DroolsKieModuleBuilder moduleBuilder) {
        this.activeKieContainer = new AtomicReference<>(initialKieContainer);
        this.ruleBuilderService = ruleBuilderService;
        this.bundleRuleBuilderService = bundleRuleBuilderService;
        this.moduleBuilder = moduleBuilder;
    }

    /**
     * Retrieves the currently active KieContainer.
     * @return The active KieContainer.
     */
    public KieContainer getKieContainer() {
        return activeKieContainer.get();
    }

    // -------------------------------------------------------------------------
    // Worker Method (Relies on existing BankContextHolder for filtering)
    // -------------------------------------------------------------------------

    /**
     * Fetches rules from the DB, compiles the DRL, and swaps the active KieContainer.
     * This method relies on the current thread having the correct bankId set
     * in the BankContextHolder.
     * @return true if compilation was successful, false otherwise.
     */
    @Transactional(readOnly = true)
    public boolean reloadKieContainer() {
        KieServices kieServices = KieServices.Factory.get();

        try {
            // 1. Fetch DRL content from the database. The @Transactional proxy now
            //    runs, starting a transaction and enabling the Hibernate filter.
            String productRuleContent = ruleBuilderService.buildAllRulesForCompilation();
            String bundleRuleContent = bundleRuleBuilderService.buildAllRulesForCompilation();

            // 2. Prepare content map
            Map<String, String> drlContent = Map.of(
                    DroolsKieModuleBuilder.PRODUCT_RULES_PATH, productRuleContent,
                    DroolsKieModuleBuilder.BUNDLE_RULES_PATH, bundleRuleContent
            );

            // 3. Delegate the build, installation, and error checking
            ReleaseId releaseId = moduleBuilder.buildAndInstallKieModule(drlContent);

            // 4. Create the new container and swap the reference (thread-safe update)
            KieContainer newContainer = kieServices.newKieContainer(releaseId);
            activeKieContainer.set(newContainer);

            System.out.println("✅ Drools KieContainer successfully reloaded with new rules.");
            return true;

        } catch (RuntimeException e) {
            System.err.println("❌ DROOLS COMPILATION ERROR during reload!");
            System.err.println(e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Overloaded Method (For Test/Manual Context Override)
    // -------------------------------------------------------------------------

    /**
     * Overload to execute rule reload within a specific bank context.
     * This is intended for integration tests or maintenance calls where the
     * BankContextHolder may not be correctly set by the request flow.
     *
     * @param bankId The bankId to use for the rule compilation database read.
     * @return true if compilation was successful, false otherwise.
     */
    public boolean reloadKieContainer(String bankId) {
        if (bankId == null) {
            // Use 'self' to call the transactional method
            return self.reloadKieContainer();
        }

        // 1. SAVE the current context (CRITICAL for thread safety)
        String previousBankId = BankContextHolder.getBankId();
        try {
            // 2. SET the required bankId for the DB read
            BankContextHolder.setBankId(bankId);

            // 3. Delegate to the worker method using the proxy
            return self.reloadKieContainer(); // <-- Use 'self' here

        } finally {
            // 4. RESTORE the original context (CRITICAL)
            BankContextHolder.setBankId(previousBankId);
        }
    }
}