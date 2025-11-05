package com.bankengine.rules.service;

import com.bankengine.config.DroolsConfig;
import com.bankengine.pricing.service.DroolsRuleBuilderService;
import org.kie.api.KieServices;
import org.kie.api.builder.*;
import org.kie.api.builder.model.KieBaseModel;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.builder.model.KieSessionModel;
import org.kie.api.conf.EqualityBehaviorOption;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.runtime.KieContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class KieContainerReloadService {

    // Holds the currently active KieContainer instance
    private final AtomicReference<KieContainer> activeKieContainer;

    private final DroolsRuleBuilderService ruleBuilderService;

    // Release ID constants must match DroolsConfig
    private static final String GROUP_ID = "com.bankengine";
    private static final String ARTIFACT_ID = "plexus-rules";
    private static final String VERSION = "1.0.0";
    private static final ReleaseId RELEASE_ID = KieServices.Factory.get().newReleaseId(GROUP_ID, ARTIFACT_ID, VERSION);

    // KIE Base/Session constants must match DroolsConfig
    private static final String KBASE_NAME = DroolsConfig.KBASE_NAME;
    private static final String KSESSION_NAME = DroolsConfig.KSESSION_NAME;

    @Autowired
    public KieContainerReloadService(KieContainer initialKieContainer, DroolsRuleBuilderService ruleBuilderService) {
        // Initialize the atomic reference with the container built by DroolsConfig
        this.activeKieContainer = new AtomicReference<>(initialKieContainer);
        this.ruleBuilderService = ruleBuilderService;
    }

    /**
     * Retrieves the currently active KieContainer.
     * @return The active KieContainer.
     */
    public KieContainer getKieContainer() {
        return activeKieContainer.get();
    }

    /**
     * Fetches rules from the DB, compiles the DRL, and swaps the active KieContainer.
     * @return true if compilation was successful, false otherwise.
     */
    public boolean reloadKieContainer() {
        KieServices kieServices = KieServices.Factory.get();

        // 1. Fetch DRL content from the database
        String ruleContent = ruleBuilderService.buildAllRulesForCompilation();

        // 2. Setup the virtual file system
        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();

        // 3. Programmatically define the KieModule configuration (must match DroolsConfig)
        KieModuleModel kModuleModel = createKieModuleModel(kieServices);
        kieFileSystem.writeKModuleXML(kModuleModel.toXML());

        // 4. Write the new DRL content and POM
        // Use the same file name as DroolsConfig for consistency, though it's rewritten here.
        kieFileSystem.write("src/main/resources/rules/initial_rules.drl", ruleContent);
        kieFileSystem.generateAndWritePomXML(RELEASE_ID);

        // 5. Compile the rules
        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
        kieBuilder.buildAll();

        // 6. Check for errors
        if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
            System.err.println("❌ DROOLS COMPILATION ERROR during reload!");
            System.err.println(kieBuilder.getResults().toString());
            // Log the problematic DRL content for debugging
            System.err.println("Failed DRL Content:\n" + ruleContent);
            return false;
        }

        // 7. Update the active KieContainer instance
        KieModule kieModule = kieBuilder.getKieModule();
        // Register the new module version
        kieServices.getRepository().addKieModule(kieModule);

        // Create the new container and swap the reference (thread-safe update)
        KieContainer newContainer = kieServices.newKieContainer(kieModule.getReleaseId());
        activeKieContainer.set(newContainer);

        System.out.println("✅ Drools KieContainer successfully reloaded with new rules.");
        return true;
    }

    /**
     * Helper method to programmatically create a minimal, valid kmodule.xml equivalent.
     * MUST match the configuration in DroolsConfig.
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