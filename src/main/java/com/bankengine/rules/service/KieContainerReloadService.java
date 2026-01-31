package com.bankengine.rules.service;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.config.drools.DroolsKieModuleBuilder;
import com.bankengine.pricing.service.BundleRuleBuilderService;
import com.bankengine.pricing.service.ProductRuleBuilderService;
import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class KieContainerReloadService {

    private final AtomicReference<KieContainer> activeKieContainer;

    @Autowired
    @Lazy
    private KieContainerReloadService self;

    private final ProductRuleBuilderService productRuleBuilderService;
    private final BundleRuleBuilderService bundleRuleBuilderService;
    private final DroolsKieModuleBuilder moduleBuilder;

    @Autowired
    public KieContainerReloadService(KieContainer initialKieContainer,
                                     ProductRuleBuilderService productRuleBuilderService,
                                     BundleRuleBuilderService bundleRuleBuilderService,
                                     DroolsKieModuleBuilder moduleBuilder) {
        this.activeKieContainer = new AtomicReference<>(initialKieContainer);
        this.productRuleBuilderService = productRuleBuilderService;
        this.bundleRuleBuilderService = bundleRuleBuilderService;
        this.moduleBuilder = moduleBuilder;
    }

    public KieContainer getKieContainer() {
        return activeKieContainer.get();
    }

    /**
     * Fetches rules from the DB, compiles the DRL, and swaps the active KieContainer.
     * Throws RuntimeException if compilation fails.
     */
    @Transactional(readOnly = true)
    @CacheEvict(value = {"publicCatalog", "productDetails", "productPricingLinks"}, allEntries = true)
    public void reloadKieContainer() {
        KieServices kieServices = KieServices.Factory.get();

        try {
            String productRuleContent = productRuleBuilderService.buildAllRulesForCompilation();
            String bundleRuleContent = bundleRuleBuilderService.buildAllRulesForCompilation();

            Map<String, String> drlContent = getDrlContent(productRuleContent, bundleRuleContent);

            ReleaseId releaseId = moduleBuilder.buildAndInstallKieModule(drlContent);
            KieContainer newContainer = kieServices.newKieContainer(releaseId);
            activeKieContainer.set(newContainer);

            System.out.println("✅ Drools KieContainer successfully reloaded.");

        } catch (RuntimeException e) {
            System.err.println("❌ DROOLS COMPILATION ERROR during reload!");
            throw e;
        }
    }

    private static Map<String, String> getDrlContent(String productRuleContent, String bundleRuleContent) {
        String bankId = TenantContextHolder.getBankId();
        String safeBankId = (bankId != null)
                ? bankId.toLowerCase().replaceAll("[^a-z0-9]", "")
                : "system";

        String productPath = String.format(DroolsKieModuleBuilder.PRODUCT_RULES_PATH, safeBankId);
        String bundlePath = String.format(DroolsKieModuleBuilder.BUNDLE_RULES_PATH, safeBankId);

        return Map.of(
                productPath, productRuleContent,
                bundlePath, bundleRuleContent
        );
    }

    /**
     * Overload to execute rule reload within a specific bank context.
     * Changed return type to void to match the worker method.
     *
     * @param bankId The bankId to use for the rule compilation database read.
     */
    public void reloadKieContainer(String bankId) {
        if (bankId == null) {
            self.reloadKieContainer();
            return;
        }

        String previousBankId = TenantContextHolder.getBankId();
        try {
            TenantContextHolder.setBankId(bankId);
            // Use 'self' proxy to ensure @Transactional on the worker method is honored
            self.reloadKieContainer();
        } finally {
            TenantContextHolder.setBankId(previousBankId);
        }
    }
}