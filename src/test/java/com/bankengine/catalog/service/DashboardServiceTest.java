package com.bankengine.catalog.service;

import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.auth.repository.RoleRepository;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.repository.PricingTierRepository;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.catalog.dto.DashboardStatsResponse;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.test.config.BaseServiceTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest extends BaseServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductTypeRepository productTypeRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PricingComponentRepository pricingComponentRepository;
    @Mock private PricingTierRepository pricingTierRepository;
    @Mock private BankConfigurationRepository bankConfigurationRepository;

    @InjectMocks
    private DashboardService service;

    @Test
    void testGetLocalStats() {
        when(productRepository.countByStatus(any())).thenReturn(1L);
        when(productTypeRepository.countByStatus(any())).thenReturn(1L);
        when(pricingComponentRepository.countByStatus(any())).thenReturn(1L);
        when(roleRepository.count()).thenReturn(1L);
        when(pricingTierRepository.count()).thenReturn(1L);

        DashboardStatsResponse.StatsSet stats = service.getLocalStats();
        assertNotNull(stats);
        assertEquals(1L, stats.getRoles().get("ACTIVE"));
    }

    @Test
    void testGetGlobalStats() {
        when(productRepository.countByStatus(any())).thenReturn(1L);
        when(productTypeRepository.countByStatus(any())).thenReturn(1L);
        when(pricingComponentRepository.countByStatus(any())).thenReturn(1L);
        when(roleRepository.count()).thenReturn(1L);
        when(pricingTierRepository.count()).thenReturn(1L);
        when(bankConfigurationRepository.count()).thenReturn(5L);

        DashboardStatsResponse.StatsSet stats = service.getGlobalStats();
        assertNotNull(stats);
        assertEquals(5L, stats.getTotalBanks());
    }
}
