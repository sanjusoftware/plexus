package com.bankengine.config.drools;

import org.kie.api.KieServices;
import org.kie.api.builder.*;
import org.kie.api.builder.model.KieBaseModel;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.builder.model.KieSessionModel;
import org.kie.api.conf.EqualityBehaviorOption;
import org.kie.api.conf.EventProcessingOption;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Encapsulates the core logic for programmatically creating, building, and installing a KieModule.
 * This class ensures that both DroolsConfig (startup) and KieContainerReloadService (runtime)
 * use identical configuration and build processes.
 */
@Component
public class DroolsKieModuleBuilder {

    // --- Shared Constants ---
    public static final String GROUP_ID = "com.bankengine";
    public static final String ARTIFACT_ID = "plexus-rules";
    public static final String VERSION = "1.0.0";
    public static final String KBASE_NAME = "KBase1";
    public static final String KSESSION_NAME = "KSession1";
    public static final String PRODUCT_RULES_PATH = "src/main/resources/rules/product_rules.drl";
    public static final String BUNDLE_RULES_PATH = "src/main/resources/rules/bundle_rules.drl";

    private final KieServices kieServices = KieServices.Factory.get();
    private final ReleaseId releaseId = kieServices.newReleaseId(GROUP_ID, ARTIFACT_ID, VERSION);

    /**
     * Programmatically creates the kmodule.xml equivalent configuration.
     * This ensures the KIE Base and Session are defined identically for initial load and reload.
     */
    private KieModuleModel createKieModuleModel() {
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

    /**
     * Builds and installs a new KieModule using the provided DRL content.
     * @param drlContent A map where key is the DRL file path and value is the content.
     * @return The ReleaseId of the newly installed KieModule.
     * @throws RuntimeException if Drools compilation fails.
     */
    public ReleaseId buildAndInstallKieModule(Map<String, String> drlContent) {
        // 1. Setup the virtual file system (KieFileSystem)
        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();

        // 2. Programmatically define the KieModule configuration
        KieModuleModel kModuleModel = createKieModuleModel();
        kieFileSystem.writeKModuleXML(kModuleModel.toXML());

        // 3. Write all DRL content to the file system
        drlContent.forEach(kieFileSystem::write);

        // 4. Define the release ID and write the POM
        kieFileSystem.generateAndWritePomXML(releaseId);

        // 5. Build the KieModule
        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
        kieBuilder.buildAll();

        // 6. Check for errors
        if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
            // Propagate exception to fail application startup or reload operation
            throw new RuntimeException("Drools build errors during KieModule generation:\n"
                    + kieBuilder.getResults().toString());
        }

        // 7. Register the new KieModule in the repository
        KieModule kieModule = kieBuilder.getKieModule();
        kieServices.getRepository().addKieModule(kieModule);

        return releaseId;
    }

    public ReleaseId getReleaseId() {
        return releaseId;
    }
}