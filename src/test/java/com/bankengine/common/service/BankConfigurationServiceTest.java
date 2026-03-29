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
import com.bankengine.test.config.BaseServiceTest;
import com.bankengine.web.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankConfigurationServiceTest extends BaseServiceTest {

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
        standardRequest.setBankId(TEST_BANK_ID);
        standardRequest.setAllowProductInMultipleBundles(true);
        standardRequest.setIssuerUrl("https://test-bank-isser");
        standardRequest.setCurrencyCode("INR");
        standardRequest.setCategoryConflictRules(new ArrayList<>(List.of(
                new BankConfigurationRequest.CategoryConflictDto("RETAIL", "CORPORATE")
        )));
    }

    @Test
    @DisplayName("CreateBank should save config but NOT create super admin role yet")
    void createBank_ShouldSaveConfigButNotCreateRole() {
        // Arrange
        TenantContextHolder.setBankId("SYSTEM");
        standardRequest.setClientSecret("secret123");
        when(bankConfigurationRepository.findByBankIdUnfiltered(TEST_BANK_ID)).thenReturn(Optional.empty());
        when(bankConfigurationRepository.findByBankIdUnfiltered("SYSTEM")).thenReturn(Optional.empty());

        BankConfigurationResponse response = bankConfigurationService.createBank(standardRequest);

        assertNotNull(response);
        assertEquals(TEST_BANK_ID, response.getBankId());
        assertEquals(standardRequest.getIssuerUrl(), response.getIssuerUrl());
        assertEquals(standardRequest.getCurrencyCode(), response.getCurrencyCode());
        assertTrue(response.isAllowProductInMultipleBundles());
        assertTrue(response.isHasClientSecret());

        ArgumentCaptor<BankConfiguration> configCaptor = ArgumentCaptor.forClass(BankConfiguration.class);
        verify(bankConfigurationRepository).save(configCaptor.capture());
        assertEquals("secret123", configCaptor.getValue().getClientSecret());

        // Should NOT create role yet
        verify(roleRepository, never()).save(any());
        verify(permissionMappingService, never()).evictAllRolePermissionsCache();
    }

    @Test
    @DisplayName("ActivateBank should set status to ACTIVE and create BANK_ADMIN role")
    void activateBank_ShouldSetStatusAndCreateRole() {
        // Arrange
        BankConfiguration config = new BankConfiguration();
        config.setBankId(TEST_BANK_ID);
        config.setCategoryConflictRules(new ArrayList<>());
        when(bankConfigurationRepository.findByBankIdUnfiltered(TEST_BANK_ID)).thenReturn(Optional.of(config));
        when(roleRepository.findByNameAndBankId("BANK_ADMIN", TEST_BANK_ID)).thenReturn(Optional.empty());
        when(authorityDiscoveryService.discoverAllAuthorities()).thenReturn(Set.of("catalog:read", "system:admin"));

        // Act
        BankConfigurationResponse response = bankConfigurationService.activateBank(TEST_BANK_ID);

        // Assert
        assertEquals("ACTIVE", response.getStatus());
        verify(bankConfigurationRepository).save(config);

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(roleCaptor.capture());

        Role savedRole = roleCaptor.getValue();
        assertEquals("BANK_ADMIN", savedRole.getName());
        assertEquals(TEST_BANK_ID, savedRole.getBankId());
        assertTrue(savedRole.getAuthorities().contains("catalog:read"));
        assertFalse(savedRole.getAuthorities().contains("system:admin"), "System authorities should be filtered out");
        assertTrue(savedRole.getAuthorities().contains("bank:config:read"));
        assertTrue(savedRole.getAuthorities().contains("bank:config:write"));

        verify(permissionMappingService).evictAllRolePermissionsCache();
    }

    @Test
    @DisplayName("RejectBank should set status to REJECTED and delete BANK_ADMIN role if it exists")
    void rejectBank_ShouldSetStatusAndRemoveRole() {
        // Arrange
        BankConfiguration config = new BankConfiguration();
        config.setBankId(TEST_BANK_ID);
        config.setStatus(com.bankengine.common.model.BankStatus.DRAFT);
        config.setCategoryConflictRules(new ArrayList<>());
        when(bankConfigurationRepository.findByBankIdUnfiltered(TEST_BANK_ID)).thenReturn(Optional.of(config));

        Role existingRole = new Role();
        existingRole.setName("BANK_ADMIN");
        existingRole.setBankId(TEST_BANK_ID);
        when(roleRepository.findByNameAndBankId("BANK_ADMIN", TEST_BANK_ID)).thenReturn(Optional.of(existingRole));

        // Act
        BankConfigurationResponse response = bankConfigurationService.rejectBank(TEST_BANK_ID);

        // Assert
        assertEquals("REJECTED", response.getStatus());
        verify(bankConfigurationRepository).save(config);
        verify(roleRepository).delete(existingRole);
    }

    @Test
    @DisplayName("UpdateBank should update fields when bank exists and tenant matches")
    void updateBank_ShouldUpdateExistingConfig() {
        BankConfiguration existing = new BankConfiguration();
        existing.setBankId(TEST_BANK_ID);
        existing.setCategoryConflictRules(new ArrayList<>());
        when(bankConfigurationRepository.findByBankId(TEST_BANK_ID)).thenReturn(Optional.of(existing));

        standardRequest.setClientSecret("new-secret");
        BankConfigurationResponse response = bankConfigurationService.updateBank(standardRequest);

        assertNotNull(response);
        assertTrue(response.isAllowProductInMultipleBundles());
        assertTrue(response.isHasClientSecret());
        assertEquals("new-secret", existing.getClientSecret());
        verify(bankConfigurationRepository).save(existing);
    }

    @Test
    @DisplayName("GetBank should throw NotFound when bank ID does not exist in repository")
    void getBank_WhenNotExists_ShouldThrowNotFound() {
       when(bankConfigurationRepository.findByBankId(TEST_BANK_ID)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> bankConfigurationService.getBank(TEST_BANK_ID));
    }

    @Test
    @DisplayName("UpdateBank should trigger lambda branch when bank missing")
    void updateBank_WhenNotExists_ShouldTriggerLambda() {
        TenantContextHolder.setBankId("SYSTEM");
        String missingId = "MISSING";
        standardRequest.setBankId(missingId);
        when(bankConfigurationRepository.findByBankId(missingId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () ->
                bankConfigurationService.updateBank(standardRequest)
        );
    }

    @Test
    @DisplayName("ValidateTenantAccess should allow SYSTEM to view any bank")
    void getBank_WhenSystemMode_ShouldSucceedRegardlessOfTenant() {
        // Arrange
        String targetBankId = "BANK_A";
        BankConfiguration config = new BankConfiguration();
        config.setBankId(targetBankId);

        // Mock System user requesting a different bank
        TenantContextHolder.setBankId("SYSTEM");
        when(bankConfigurationRepository.findByBankId(targetBankId)).thenReturn(Optional.of(config));
        assertDoesNotThrow(() -> bankConfigurationService.getBank(targetBankId));
    }

    @Test
    @DisplayName("CreateBank should handle null conflict rules gracefully")
    void createBank_WithNullRules_ShouldSucceed() {
        TenantContextHolder.setBankId("SYSTEM");
        standardRequest.setCategoryConflictRules(null);
        when(bankConfigurationRepository.findByBankIdUnfiltered(TEST_BANK_ID)).thenReturn(Optional.empty());
        when(bankConfigurationRepository.findByBankIdUnfiltered("SYSTEM")).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> bankConfigurationService.createBank(standardRequest));
        verify(bankConfigurationRepository).save(argThat(config ->
            config.getCategoryConflictRules() == null || config.getCategoryConflictRules().isEmpty()
        ));
    }
}