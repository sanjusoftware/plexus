package com.bankengine.pricing.service;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.common.service.BaseService;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingInputMetadata;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.TierCondition;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.service.drl.DroolsExpressionBuilder;
import com.bankengine.rules.service.KieContainerReloadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractRuleBuilderService extends BaseService {

    protected final PricingComponentRepository componentRepository;
    protected final PricingInputMetadataService metadataService;
    protected final DroolsExpressionBuilder droolsExpressionBuilder;

    @Autowired
    @Lazy
    protected KieContainerReloadService kieContainerReloadService;

    public AbstractRuleBuilderService(
            PricingComponentRepository componentRepository,
            PricingInputMetadataService metadataService,
            DroolsExpressionBuilder droolsExpressionBuilder) {
        this.componentRepository = componentRepository;
        this.metadataService = metadataService;
        this.droolsExpressionBuilder = droolsExpressionBuilder;
    }

    // --- Template Methods (Subclasses must implement these) ---
    protected abstract String getFactType();

    protected abstract String getPackageSubPath();

    protected abstract String buildRHSAction(PricingComponent component, PricingTier tier);

    protected abstract List<PricingComponent> fetchComponents();

    /**
     * Public orchestration method to refresh Drools rules.
     * This is called by both Admin UI (on save) and Integration Tests (on setup).
     */
    public void rebuildRules() {
        kieContainerReloadService.reloadKieContainer(getSafeBankIdForDrl());
    }

    // --- Core Orchestration ---

    public String buildAllRulesForCompilation() {
        StringBuilder drl = new StringBuilder();
        drl.append(getDrlHeader());

        List<PricingComponent> components = fetchComponents();
        for (PricingComponent component : components) {
            String body = component.getPricingTiers().stream()
                    .map(tier -> buildSingleRule(component, tier))
                    .collect(Collectors.joining("\n\n"));
            drl.append(body).append("\n\n");
        }

        String finalDrl = components.isEmpty() ? buildPlaceholderRules() : drl.toString();

        // --- DEBUG LOGGING ADDED HERE ---
        logGeneratedDrl(finalDrl);

        return finalDrl;
    }

    private void logGeneratedDrl(String drl) {
        log.info("========================================================");
        log.info("GENERATED DRL FOR PACKAGE: " + getPackageSubPath());
        log.info("========================================================");
        log.info(drl);
        log.info("========================================================");
    }

    private String buildSingleRule(PricingComponent component, PricingTier tier) {
        String ruleName = String.format("%s_%s_%s_Tier_%d",
                getPackageSubPath().toUpperCase(), getSafeBankIdForDrl(),
                component.getName().replaceAll("\\s", "_"), tier.getId());

        // Added a debug print to the RHS (then) of the rule so you know if it fires
        String rhsWithLogging = String.format("""
                System.out.println("🔥 Rule Fired: %s");
                %s
                """, ruleName, buildRHSAction(component, tier));

        return String.format("""
                rule "%s"
                    no-loop true
                    salience %d
                    when
                %s
                    then
                %s
                end""", ruleName, tier.getId(), buildLHSCondition(tier, component.getId()), rhsWithLogging);
    }

    // --- Common Shared Logic ---

    protected String getSafeBankIdForDrl() {
        String bankId = null;
        try {
            bankId = getCurrentBankId();
        } catch (IllegalStateException e) {
            // This is expected during startup/tests
        }

        if (bankId == null) {
            if (TenantContextHolder.isSystemMode()) {
                return "system"; // Fallback for startup
            }
            // If not in system mode and still null, the context is broken
            throw new IllegalStateException("Bank ID is missing and System Mode is OFF.");
        }
        return bankId;
    }

    protected String getDrlHeader() {
        String bankId = getSafeBankIdForDrl();
        String safeBankId = bankId.toLowerCase().replaceAll("[^a-z0-9]", "");
        String factImport = getFactType();

        return String.format("""
                package bankengine.%s.rules.%s;
                
                import %s;
                import com.bankengine.pricing.model.PriceValue;
                import java.math.BigDecimal;
                import java.time.LocalDate;
                
                """, getPackageSubPath(), safeBankId, factImport);
    }

    protected String buildLHSCondition(PricingTier tier, Long componentId) {
        if (tier.getId() == null || tier.getId() <= 0) {
            throw new IllegalStateException("Cannot build DRL because Tier ID is missing.");
        }

        String bankId = getSafeBankIdForDrl();
        String factName = getFactType().substring(getFactType().lastIndexOf(".") + 1);

        StringBuilder conditionBuilder = new StringBuilder();

        // 1. Check Bank Context
        conditionBuilder.append(String.format("bankId == \"%s\"", bankId));

        // 2. Check Targeted Tier ID (Date-Aware Filter) - activePricingTierIds
        // This is the "kill switch" for rules whose effective dates haven't arrived.
        conditionBuilder.append(String.format(", activePricingTierIds contains %dL", tier.getId()));

        // 3. Append User-Defined Tier Conditions (Segment, Amount, etc.)
        if (tier.getConditions() != null && !tier.getConditions().isEmpty()) {
            conditionBuilder.append(", ");
            Iterator<TierCondition> it = tier.getConditions().iterator();
            while (it.hasNext()) {
                TierCondition cond = it.next();
                PricingInputMetadata metadata = metadataService.getMetadataEntityByKey(cond.getAttributeName());
                conditionBuilder.append(droolsExpressionBuilder.buildExpression(cond, metadata));

                if (it.hasNext()) {
                    String connector = cond.getConnector() != null ? cond.getConnector().name() : "AND";
                    conditionBuilder.append(" ").append(connector).append(" ");
                }
            }
        }
        return String.format("        $input : %s ( %s )", factName, conditionBuilder.toString().trim());
    }

    protected String buildPlaceholderRules() {
        String factName = getFactType().substring(getFactType().lastIndexOf(".") + 1);
        return getDrlHeader() + String.format("""
                rule "Placeholder_%s"
                    when
                        $input : %s ( )
                    then
                        // No rules defined for this tenant
                end
                """, getPackageSubPath(), factName);
    }
}