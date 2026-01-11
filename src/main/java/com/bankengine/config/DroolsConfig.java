package com.bankengine.config;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.config.drools.DroolsKieModuleBuilder;
import com.bankengine.pricing.service.BundleRuleBuilderService;
import com.bankengine.pricing.service.ProductRuleBuilderService;
import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.Map;

@Configuration
public class DroolsConfig {

    @Autowired
    @Lazy
    private ProductRuleBuilderService ruleBuilderService;

    @Autowired
    @Lazy
    private BundleRuleBuilderService bundleRuleBuilderService;

    @Autowired
    private DroolsKieModuleBuilder moduleBuilder;

    @Bean
    @Lazy
    public KieContainer kieContainer() {
        KieServices kieServices = KieServices.Factory.get();

        try {
            // 1. Elevate to System Mode to bypass the bankId requirement during startup
            TenantContextHolder.setSystemMode(true);

            // 2. Fetch DRL content (Aspect will now see null bankId but permit it)
            String productRuleContent = ruleBuilderService.buildAllRulesForCompilation();
            String bundleRuleContent = bundleRuleBuilderService.buildAllRulesForCompilation();

            // 3. Prepare content map
            Map<String, String> drlContent = Map.of(
                    DroolsKieModuleBuilder.PRODUCT_RULES_PATH, productRuleContent,
                    DroolsKieModuleBuilder.BUNDLE_RULES_PATH, bundleRuleContent
            );

            // 4. Build the KieModule
            ReleaseId releaseId = moduleBuilder.buildAndInstallKieModule(drlContent);

            // 5. Return the KieContainer associated with the new ReleaseId
            return kieServices.newKieContainer(releaseId);

        } finally {
            // 6. CRITICAL: Always clear system mode and context to prevent leaks
            // to subsequent threads or logic
            TenantContextHolder.clear();
        }
    }
}