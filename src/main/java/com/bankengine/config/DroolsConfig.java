package com.bankengine.config;

import com.bankengine.pricing.service.DroolsRuleBuilderService;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.model.KieBaseModel;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.builder.model.KieSessionModel;
import org.kie.api.conf.EqualityBehaviorOption;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.runtime.KieContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class DroolsConfig {

    @Autowired
    private DroolsRuleBuilderService ruleBuilderService;

    // Define consistent IDs for your dynamic module
    private static final String GROUP_ID = "com.bankengine";
    private static final String ARTIFACT_ID = "plexus-rules";
    private static final String VERSION = "1.0.0";

    // Define the default KIE Base/Session names
    public static final String KBASE_NAME = "KBase1";
    public static final String KSESSION_NAME = "KSession1";

    @Bean
    @Lazy // This prevents synchronous blocking during startup
    public KieContainer kieContainer() {
        KieServices kieServices = KieServices.Factory.get();

        // 1. Programmatically define the KieModule configuration
        KieModuleModel kModuleModel = createKieModuleModel(kieServices);

        // 2. Setup the virtual file system (KieFileSystem)
        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();

        // Write the generated kmodule.xml to the virtual file system
        kieFileSystem.writeKModuleXML(kModuleModel.toXML());

        // Add the rule content to the file system
        String ruleContent = ruleBuilderService.buildAllRulesForCompilation();
        kieFileSystem.write("src/main/resources/bankengine/pricing/rules/initial_rules.drl", ruleContent);

        // Define the release ID and write the POM
        ReleaseId releaseId = kieServices.newReleaseId(GROUP_ID, ARTIFACT_ID, VERSION);
        kieFileSystem.generateAndWritePomXML(releaseId);

        // 3. Build the KieModule
        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
        kieBuilder.buildAll();

        // 4. Check for errors
        if (kieBuilder.getResults().hasMessages(org.kie.api.builder.Message.Level.ERROR)) {
            throw new RuntimeException("Drools build errors:\n"
                    + kieBuilder.getResults().toString());
        }

        // 5. Register and return the KieContainer
        KieModule kieModule = kieBuilder.getKieModule();
        kieServices.getRepository().addKieModule(kieModule);

        return kieServices.newKieContainer(releaseId);
    }

    /**
     * Helper method to programmatically create a minimal, valid kmodule.xml equivalent.
     */
    private KieModuleModel createKieModuleModel(KieServices kieServices) {
        KieModuleModel kModuleModel = kieServices.newKieModuleModel();

        // Define a default KIE Base
        KieBaseModel kBaseModel = kModuleModel.newKieBaseModel(KBASE_NAME)
                .setDefault(true)
                .addPackage("*")
                .setEqualsBehavior(EqualityBehaviorOption.EQUALITY)
                .setEventProcessingMode(EventProcessingOption.CLOUD);

        // Define a default KIE Session within that base
        kBaseModel.newKieSessionModel(KSESSION_NAME)
                .setDefault(true)
                .setType(KieSessionModel.KieSessionType.STATEFUL);

        return kModuleModel;
    }
}