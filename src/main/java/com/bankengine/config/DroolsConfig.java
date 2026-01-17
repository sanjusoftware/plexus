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
    private ProductRuleBuilderService productRuleBuilderService;

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
            TenantContextHolder.setSystemMode(true);
            String productRuleContent = productRuleBuilderService.buildAllRulesForCompilation();
            String bundleRuleContent = bundleRuleBuilderService.buildAllRulesForCompilation();
            String safePathId = "system";
            String productPath = String.format(DroolsKieModuleBuilder.PRODUCT_RULES_PATH, safePathId);
            String bundlePath = String.format(DroolsKieModuleBuilder.BUNDLE_RULES_PATH, safePathId);

            Map<String, String> drlContent = Map.of(
                    productPath, productRuleContent,
                    bundlePath, bundleRuleContent
            );
            ReleaseId releaseId = moduleBuilder.buildAndInstallKieModule(drlContent);
            return kieServices.newKieContainer(releaseId);
        } finally {
            TenantContextHolder.clear();
        }
    }
}