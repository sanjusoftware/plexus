package com.bankengine.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {
    private StatsSet local;
    private StatsSet global; // Only for SYSTEM_ADMIN

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatsSet {
        private Map<String, Long> products;         // Status -> Count
        private Map<String, Long> productTypes;
        private Map<String, Long> roles;
        private Map<String, Long> pricingComponents;
        private Map<String, Long> pricingTiers;
        private Long totalBanks; // Only relevant for global
    }
}
