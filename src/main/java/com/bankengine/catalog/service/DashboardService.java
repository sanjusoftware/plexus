package com.bankengine.catalog.service;

import com.bankengine.auth.model.Role;
import com.bankengine.auth.repository.RoleRepository;
import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.repository.PricingTierRepository;
import com.bankengine.catalog.dto.DashboardStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProductRepository productRepository;
    private final ProductTypeRepository productTypeRepository;
    private final RoleRepository roleRepository;
    private final PricingComponentRepository pricingComponentRepository;
    private final PricingTierRepository pricingTierRepository;
    private final BankConfigurationRepository bankConfigurationRepository;

    @Transactional(readOnly = true)
    public DashboardStatsResponse.StatsSet getLocalStats() {
        // Ensure we are NOT in system mode to get tenant-filtered results
        boolean originalMode = TenantContextHolder.isSystemMode();
        try {
            TenantContextHolder.setSystemMode(false);
            return fetchStatsSet();
        } finally {
            TenantContextHolder.setSystemMode(originalMode);
        }
    }

    @Transactional(readOnly = true)
    public DashboardStatsResponse.StatsSet getGlobalStats() {
        boolean originalMode = TenantContextHolder.isSystemMode();
        try {
            TenantContextHolder.setSystemMode(true);
            DashboardStatsResponse.StatsSet globalStats = fetchStatsSet();
            globalStats.setTotalBanks(bankConfigurationRepository.count());
            return globalStats;
        } finally {
            TenantContextHolder.setSystemMode(originalMode);
        }
    }

    private DashboardStatsResponse.StatsSet fetchStatsSet() {
        return DashboardStatsResponse.StatsSet.builder()
                .products(countProductsByStatus())
                .productTypes(countProductTypesByStatus())
                .roles(Map.of("ACTIVE", roleRepository.count()))
                .pricingComponents(countPricingComponentsByStatus())
                .pricingTiers(Map.of("ACTIVE", pricingTierRepository.count()))
                .build();
    }

    private Map<String, Long> countProductsByStatus() {
        Map<String, Long> counts = new java.util.HashMap<>();
        for (VersionableEntity.EntityStatus status : VersionableEntity.EntityStatus.values()) {
            counts.put(status.name(), productRepository.countByStatus(status));
        }
        return counts;
    }

    private Map<String, Long> countProductTypesByStatus() {
        Map<String, Long> counts = new java.util.HashMap<>();
        for (VersionableEntity.EntityStatus status : VersionableEntity.EntityStatus.values()) {
            counts.put(status.name(), productTypeRepository.countByStatus(status));
        }
        return counts;
    }

    private Map<String, Long> countPricingComponentsByStatus() {
        Map<String, Long> counts = new java.util.HashMap<>();
        for (VersionableEntity.EntityStatus status : VersionableEntity.EntityStatus.values()) {
            counts.put(status.name(), pricingComponentRepository.countByStatus(status));
        }
        return counts;
    }
}
