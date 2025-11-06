package com.bankengine.rules.controller;

import com.bankengine.rules.service.KieContainerReloadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Rule Management", description = "Endpoints for dynamic control and reloading of the Drools ruleset.")
@RestController
@RequestMapping("/api/v1/rules")
public class RuleManagementController {

    private final KieContainerReloadService reloadService;

    public RuleManagementController(KieContainerReloadService reloadService) {
        this.reloadService = reloadService;
    }

    /**
     * POST /api/v1/rules/reload
     * Triggers the full compilation and reloading of the Drools KieContainer.
     */
    @Operation(summary = "Triggers a full reload of the Drools rule engine",
            description = "Fetches the latest PricingComponent configuration from the database, generates the DRL, compiles the new KieContainer, and swaps it into the running application. Use this after configuration changes.")
    @PostMapping("/reload")
    public ResponseEntity<String> reloadRules() {
        boolean success = reloadService.reloadKieContainer();

        if (success) {
            return ResponseEntity.ok("✅ Drools KieContainer reloaded successfully with latest database rules.");
        } else {
            // The reloadService logs the exact compilation error
            return ResponseEntity.status(500).body("❌ Rule reload failed. Check server logs for DRL compilation errors.");
        }
    }
}