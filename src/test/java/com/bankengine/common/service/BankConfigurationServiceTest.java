package com.bankengine.common.service;

import com.bankengine.auth.model.Role;
import com.bankengine.auth.repository.RoleRepository;
import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.auth.service.AuthorityDiscoveryService;
import com.bankengine.auth.service.PermissionMappingService;
import com.bankengine.catalog.model.ProductCategory;
import com.bankengine.catalog.repository.BundleProductLinkRepository;
import com.bankengine.catalog.repository.ProductCategoryRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.common.dto.BankConfigurationRequest;
import com.bankengine.common.dto.BankConfigurationResponse;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.BankStatus;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.test.config.BaseServiceTest;
import com.bankengine.web.exception.NotFoundException;
import com.bankengine.web.exception.ValidationException;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankConfigurationServiceTest extends BaseServiceTest {

    @Mock
    private BankConfigurationRepository bankConfigurationRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductCategoryRepository productCategoryRepository;
    @Mock
    private BundleProductLinkRepository bundleProductLinkRepository;
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
        lenient().when(bundleProductLinkRepository.findAllByBankIdAndBundleStatuses(anyString(), anySet())).thenReturn(List.of());
        lenient().when(productCategoryRepository.findByBankIdAndCode(anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("CreateBank should reject null request body")
    void createBank_WithNullRequest_ShouldThrowValidationException() {
        assertThrows(ValidationException.class, () -> bankConfigurationService.createBank(null));
    }

    @Test
    @DisplayName("UpdateBank as Bank Admin on DRAFT bank should throw exception")
    void updateBank_AsBankAdmin_OnDraftBank_ShouldThrowException() {
        BankConfiguration existing = new BankConfiguration();
        existing.setBankId(TEST_BANK_ID);
        existing.setStatus(com.bankengine.common.model.BankStatus.DRAFT);
        when(bankConfigurationRepository.findByBankId(TEST_BANK_ID)).thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class, () ->
                bankConfigurationService.updateBank(standardRequest, false)
        );
    }

    @Test
    @DisplayName("UpdateBank as Bank Admin on ACTIVE bank should only update allowed fields")
    void updateBank_AsBankAdmin_OnActiveBank_ShouldOnlyUpdateAllowedFields() {
        BankConfiguration existing = new BankConfiguration();
        existing.setBankId(TEST_BANK_ID);
        existing.setStatus(com.bankengine.common.model.BankStatus.ACTIVE);
        existing.setName("Original Name");
        existing.setClientSecret("Original Secret");
        existing.setAllowProductInMultipleBundles(false);
        existing.setCategoryConflictRules(new ArrayList<>());
        when(bankConfigurationRepository.findByBankId(TEST_BANK_ID)).thenReturn(Optional.of(existing));

        standardRequest.setName("New Name");
        standardRequest.setClientSecret("New Secret");
        standardRequest.setAllowProductInMultipleBundles(true);
        standardRequest.setCategoryConflictRules(List.of(new BankConfigurationRequest.CategoryConflictDto("A", "B")));

        bankConfigurationService.updateBank(standardRequest, false);

        assertEquals("Original Name", existing.getName());
        assertEquals("Original Secret", existing.getClientSecret());
        assertTrue(existing.isAllowProductInMultipleBundles());
        assertEquals(1, existing.getCategoryConflictRules().size());
        verify(bankConfigurationRepository).save(existing);
    }

    @Test
    @DisplayName("UpdateBank as System Admin on DRAFT bank should update all fields")
    void updateBank_AsSystemAdmin_OnDraftBank_ShouldUpdateAllFields() {
        BankConfiguration existing = new BankConfiguration();
        existing.setBankId(TEST_BANK_ID);
        existing.setStatus(com.bankengine.common.model.BankStatus.DRAFT);
        existing.setName("Original Name");
        existing.setCategoryConflictRules(new ArrayList<>());
        when(bankConfigurationRepository.findByBankId(TEST_BANK_ID)).thenReturn(Optional.of(existing));

        standardRequest.setName("New Name");
        bankConfigurationService.updateBank(standardRequest, true);

        assertEquals("New Name", existing.getName());
        verify(bankConfigurationRepository).save(existing);
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
    @DisplayName("ActivateBank should reject REJECTED bank")
    void activateBank_RejectedBank_ShouldThrowException() {
        BankConfiguration config = new BankConfiguration();
        config.setBankId(TEST_BANK_ID);
        config.setStatus(BankStatus.REJECTED);
        when(bankConfigurationRepository.findByBankIdUnfiltered(TEST_BANK_ID)).thenReturn(Optional.of(config));

        assertThrows(IllegalStateException.class, () -> bankConfigurationService.activateBank(TEST_BANK_ID));
        verify(bankConfigurationRepository, never()).save(any());
    }

    @Test
    @DisplayName("ActivateBank should allow INACTIVE bank reactivation")
    void activateBank_InactiveBank_ShouldReactivate() {
        BankConfiguration config = new BankConfiguration();
        config.setBankId(TEST_BANK_ID);
        config.setStatus(BankStatus.INACTIVE);
        config.setCategoryConflictRules(new ArrayList<>());
        when(bankConfigurationRepository.findByBankIdUnfiltered(TEST_BANK_ID)).thenReturn(Optional.of(config));
        when(roleRepository.findByNameAndBankId("BANK_ADMIN", TEST_BANK_ID)).thenReturn(Optional.of(new Role()));

        BankConfigurationResponse response = bankConfigurationService.activateBank(TEST_BANK_ID);

        assertEquals("ACTIVE", response.getStatus());
        verify(bankConfigurationRepository).save(config);
        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("ActivateBank should reject ACTIVE bank")
    void activateBank_ActiveBank_ShouldThrowException() {
        BankConfiguration config = new BankConfiguration();
        config.setBankId(TEST_BANK_ID);
        config.setStatus(BankStatus.ACTIVE);
        config.setCategoryConflictRules(new ArrayList<>());
        when(bankConfigurationRepository.findByBankIdUnfiltered(TEST_BANK_ID)).thenReturn(Optional.of(config));

        assertThrows(IllegalStateException.class, () -> bankConfigurationService.activateBank(TEST_BANK_ID));
        verify(bankConfigurationRepository, never()).save(any());
    }

    @Test
    @DisplayName("SubmitOnboarding should require explicit client and map options")
    void submitOnboarding_ShouldRequireExplicitClientAndMapOptions() {
        standardRequest.setName("Test Bank");
        standardRequest.setClientId("client-onboarding");
        standardRequest.setCurrencyCode("INR");
        standardRequest.setAdminName("Admin User");
        standardRequest.setAdminEmail("admin@test.com");
        standardRequest.setCategoryConflictRules(List.of(new BankConfigurationRequest.CategoryConflictDto("retail", " wealth ")));

        when(bankConfigurationRepository.findByBankIdUnfiltered(TEST_BANK_ID)).thenReturn(Optional.empty());

        bankConfigurationService.submitOnboarding(standardRequest);

        ArgumentCaptor<BankConfiguration> configCaptor = ArgumentCaptor.forClass(BankConfiguration.class);
        verify(bankConfigurationRepository).save(configCaptor.capture());
        BankConfiguration saved = configCaptor.getValue();

        assertEquals("client-onboarding", saved.getClientId());
        assertEquals(1, saved.getCategoryConflictRules().size());
        assertEquals("RETAIL", saved.getCategoryConflictRules().get(0).getCategoryA());
        assertEquals("WEALTH", saved.getCategoryConflictRules().get(0).getCategoryB());
        verify(bankConfigurationRepository, never()).findByBankIdUnfiltered("SYSTEM");
    }

    @Test
    @DisplayName("SubmitOnboarding should throw ValidationException when clientId is missing")
    void submitOnboarding_WhenClientIdMissing_ShouldThrowValidationException() {
        standardRequest.setName("Test Bank");
        standardRequest.setClientId("   ");
        standardRequest.setCurrencyCode("INR");
        standardRequest.setAdminName("Admin User");
        standardRequest.setAdminEmail("admin@test.com");

        assertThrows(ValidationException.class, () -> bankConfigurationService.submitOnboarding(standardRequest));
        verify(bankConfigurationRepository, never()).save(any());
    }

    @Test
    @DisplayName("SubmitOnboarding should throw ValidationException when adminName is missing")
    void submitOnboarding_WhenAdminNameMissing_ShouldThrowValidationException() {
        standardRequest.setName("Test Bank");
        standardRequest.setClientId("client-onboarding");
        standardRequest.setCurrencyCode("INR");
        standardRequest.setAdminName(" ");
        standardRequest.setAdminEmail("admin@test.com");

        assertThrows(ValidationException.class, () -> bankConfigurationService.submitOnboarding(standardRequest));
        verify(bankConfigurationRepository, never()).save(any());
    }

    @Test
    @DisplayName("SubmitOnboarding should throw ValidationException when currencyCode is missing")
    void submitOnboarding_WhenCurrencyMissing_ShouldThrowValidationException() {
        standardRequest.setName("Test Bank");
        standardRequest.setClientId("client-onboarding");
        standardRequest.setCurrencyCode(" ");
        standardRequest.setAdminName("Admin User");
        standardRequest.setAdminEmail("admin@test.com");

        assertThrows(ValidationException.class, () -> bankConfigurationService.submitOnboarding(standardRequest));
        verify(bankConfigurationRepository, never()).save(any());
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
        BankConfigurationResponse response = bankConfigurationService.updateBank(standardRequest, true);

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
        TenantContextHolder.setBankId("MISSING");
        standardRequest.setBankId("MISSING");
        when(bankConfigurationRepository.findByBankId("MISSING")).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () ->
                bankConfigurationService.updateBank(standardRequest, true)
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

    @Test
    @DisplayName("ValidateRequest should throw ValidationException for empty categories")
    void validateRequest_WithEmptyCategories_ShouldThrowException() {
        standardRequest.setCategoryConflictRules(List.of(
                new BankConfigurationRequest.CategoryConflictDto("", "B")
        ));
        assertThrows(ValidationException.class, () -> bankConfigurationService.createBank(standardRequest));
    }

    @Test
    @DisplayName("ValidateRequest should throw ValidationException for self-conflicting category")
    void validateRequest_WithSelfConflict_ShouldThrowException() {
        standardRequest.setCategoryConflictRules(List.of(
                new BankConfigurationRequest.CategoryConflictDto("A", "a")
        ));
        assertThrows(ValidationException.class, () -> bankConfigurationService.createBank(standardRequest));
    }

    @Test
    @DisplayName("ValidateRequest should throw ValidationException for duplicate rules")
    void validateRequest_WithDuplicateRules_ShouldThrowException() {
        standardRequest.setCategoryConflictRules(List.of(
                new BankConfigurationRequest.CategoryConflictDto("A", "B"),
                new BankConfigurationRequest.CategoryConflictDto("b", "a")
        ));
        assertThrows(ValidationException.class, () -> bankConfigurationService.createBank(standardRequest));
    }
}