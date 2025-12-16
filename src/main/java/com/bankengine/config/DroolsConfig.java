package com.bankengine.config;

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
    private ProductRuleBuilderService ruleBuilderService;

    @Autowired
    private BundleRuleBuilderService bundleRuleBuilderService;

    @Autowired
    private DroolsKieModuleBuilder moduleBuilder;

    public static final String KBASE_NAME = DroolsKieModuleBuilder.KBASE_NAME;
    public static final String KSESSION_NAME = DroolsKieModuleBuilder.KSESSION_NAME;


    @Bean
    @Lazy
    public KieContainer kieContainer() {
        KieServices kieServices = KieServices.Factory.get();

        // 1. Fetch DRL content from services
        String productRuleContent = ruleBuilderService.buildAllRulesForCompilation();
        String bundleRuleContent = bundleRuleBuilderService.buildAllRulesForCompilation();

        // 2. Prepare content map
        Map<String, String> drlContent = Map.of(
                DroolsKieModuleBuilder.PRODUCT_RULES_PATH, productRuleContent,
                DroolsKieModuleBuilder.BUNDLE_RULES_PATH, bundleRuleContent
        );

        // 3. Build
        ReleaseId releaseId = moduleBuilder.buildAndInstallKieModule(drlContent);

        // 4. Return the KieContainer associated with the new ReleaseId
        return kieServices.newKieContainer(releaseId);
    }
}