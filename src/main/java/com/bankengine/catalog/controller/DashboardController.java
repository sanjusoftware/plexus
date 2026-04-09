package com.bankengine.catalog.controller;

import com.bankengine.catalog.dto.DashboardStatsResponse;
import com.bankengine.catalog.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "Endpoints for platform metrics and statistics.")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Retrieve local bank statistics for the dashboard",
            description = "Provides aggregated counts of products, types, roles, and pricing components for the current bank.")
    @GetMapping("/stats/local")
    @PreAuthorize("hasAuthority('bank:stats:read')")
    public ResponseEntity<DashboardStatsResponse.StatsSet> getLocalStats() {
        return ResponseEntity.ok(dashboardService.getLocalStats());
    }

    @Operation(summary = "Retrieve global platform statistics for the dashboard",
            description = "Provides aggregated counts of all products, types, roles, and pricing components across all banks.")
    @GetMapping("/stats/global")
    @PreAuthorize("hasAuthority('system:stats:read')")
    public ResponseEntity<DashboardStatsResponse.StatsSet> getGlobalStats() {
        return ResponseEntity.ok(dashboardService.getGlobalStats());
    }
}
