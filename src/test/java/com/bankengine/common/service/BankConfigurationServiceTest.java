package com.bankengine.common.service;

import com.bankengine.auth.model.Role;
import com.bankengine.auth.repository.RoleRepository;
import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.auth.service.AuthorityDiscoveryService;
import com.bankengine.auth.service.PermissionMappingService;
import com.bankengine.common.dto.BankConfigurationRequest;
import com.bankengine.common.dto.BankConfigurationResponse;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.web.exception.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankConfigurationServiceTest {

    @Mock
    private BankConfigurationRepository bankConfigurationRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private AuthorityDiscoveryService authorityDiscoveryService;
    @Mock
    private PermissionMappingService permissionMappingService;

    @InjectMocks
    private BankConfigurationService bankConfigurationService;

    private BankConfigurationRequest standardRequest;

    @BeforeEach
    void setUp() {
        standardRequest = new BankConfigurationRequest();
        standardRequest.setBankId("TEST-BANK");
        standardRequest.setAllowProductInMultipleBundles(true);
        standardRequest.setCategoryConflictRules(List.of(
                new BankConfigurationRequest.CategoryConflictDto("A", "B")
        ));
    }

    @AfterEach
    void tearDown() {
        com.bankengine.auth.security.TenantContextHolder.clear();
    }

    @Test
    void createBank_ShouldSaveConfigAndCreateSuperAdmin() {
        // Arrange
        when(authorityDiscoveryService.discoverAllAuthorities()).thenReturn(Set.of("catalog:read", "system:admin"));
        com.bankengine.auth.security.TenantContextHolder.setBankId("SYSTEM"); // or whatever to allow access

        // Act
        BankConfigurationResponse response = bankConfigurationService.createBank(standardRequest);

        // Assert
        assertNotNull(response);
        assertEquals("TEST-BANK", response.getBankId());
        assertTrue(response.isAllowProductInMultipleBundles());

        verify(bankConfigurationRepository).save(any(BankConfiguration.class));

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(roleCaptor.capture());

        Role savedRole = roleCaptor.getValue();
        assertEquals("SUPER_ADMIN", savedRole.getName());
        assertEquals("TEST-BANK", savedRole.getBankId());
        assertTrue(savedRole.getAuthorities().contains("catalog:read"));
        assertFalse(savedRole.getAuthorities().contains("system:admin"), "System authorities should be filtered out");
        assertTrue(savedRole.getAuthorities().contains("bank:config:read"));
        assertTrue(savedRole.getAuthorities().contains("bank:config:write"));

        verify(permissionMappingService).evictAllRolePermissionsCache();
    }

    @Test
    void updateBank_ShouldUpdateExistingConfig() {
        // Arrange
        BankConfiguration existing = new BankConfiguration();
        existing.setBankId("TEST-BANK");
        when(bankConfigurationRepository.findTenantAwareByBankId("TEST-BANK")).thenReturn(Optional.of(existing));
        com.bankengine.auth.security.TenantContextHolder.setBankId("TEST-BANK");

        // Act
        BankConfigurationResponse response = bankConfigurationService.updateBank("TEST-BANK", standardRequest);

        // Assert
        assertTrue(response.isAllowProductInMultipleBundles());
        verify(bankConfigurationRepository).save(existing);
    }

    @Test
    void getBank_WhenNotExists_ShouldThrowNotFound() {
        when(bankConfigurationRepository.findTenantAwareByBankId("UNKNOWN")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> bankConfigurationService.getBank("UNKNOWN"));
    }

    @Test
    @DisplayName("UpdateBank should trigger lambda branch when bank missing")
    void updateBank_WhenNotExists_ShouldTriggerLambda() {
        // Targets the lambda: .orElseThrow(() -> new NotFoundException(...))
        when(bankConfigurationRepository.findTenantAwareByBankId("MISSING")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                bankConfigurationService.updateBank("MISSING", standardRequest)
        );
    }

    @Test
    @DisplayName("ValidateTenantAccess should allow SYSTEM mode")
    void getBank_WhenSystemMode_ShouldSucceedRegardlessOfTenant() {
        BankConfiguration config = new BankConfiguration();
        config.setBankId("BANK_A");

        when(bankConfigurationRepository.findTenantAwareByBankId("BANK_A")).thenReturn(Optional.of(config));
        TenantContextHolder.setBankId("SYSTEM");

        assertDoesNotThrow(() -> bankConfigurationService.getBank("BANK_A"));
    }

    @Test
    @DisplayName("CreateBank should handle null conflict rules")
    void createBank_WithNullRules_ShouldSucceed() {
        standardRequest.setCategoryConflictRules(null);
        when(authorityDiscoveryService.discoverAllAuthorities()).thenReturn(Set.of());
        TenantContextHolder.setBankId("SYSTEM");

        assertDoesNotThrow(() -> bankConfigurationService.createBank(standardRequest));
        verify(bankConfigurationRepository).save(argThat(config -> config.getCategoryConflictRules().isEmpty()));
    }
}
