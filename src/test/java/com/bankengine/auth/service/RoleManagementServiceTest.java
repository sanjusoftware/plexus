package com.bankengine.auth.service;

import com.bankengine.auth.model.Role;
import com.bankengine.auth.repository.RoleRepository;
import com.bankengine.auth.security.TenantContextHolder;
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

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleManagementServiceTest {

    @Mock private RoleRepository roleRepository;
    @Mock private PermissionMappingService permissionMappingService;

    @InjectMocks
    private RoleManagementService roleManagementService;

    @BeforeEach
    void setup() {
        TenantContextHolder.setBankId("BANK_1");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void saveRoleMapping_ShouldCreateNewRole_WhenRoleDoesNotExist() {
        String roleName = "NEW_ROLE";
        Set<String> authorities = Set.of("read", "write");
        when(roleRepository.findByName(roleName)).thenReturn(Optional.empty());

        roleManagementService.saveRoleMapping(roleName, authorities);

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(captor.capture());
        Role savedRole = captor.getValue();

        assertThat(savedRole.getName()).isEqualTo(roleName);
        assertThat(savedRole.getAuthorities()).isEqualTo(authorities);
        assertThat(savedRole.getBankId()).isEqualTo("BANK_1");
        verify(permissionMappingService).evictAllRolePermissionsCache();
    }

    @Test
    void saveRoleMapping_ShouldUpdateExistingRole_WhenRoleExists() {
        String roleName = "EXISTING_ROLE";
        Role existingRole = new Role();
        existingRole.setName(roleName);

        when(roleRepository.findByName(roleName)).thenReturn(Optional.of(existingRole));

        roleManagementService.saveRoleMapping(roleName, Set.of("admin"));

        verify(roleRepository).save(existingRole);
        assertThat(existingRole.getAuthorities()).containsExactly("admin");
        verify(permissionMappingService).evictAllRolePermissionsCache();
    }

    @Test
    void getAuthoritiesByRoleName_ShouldThrowNotFound_WhenRoleMissing() {
        when(roleRepository.findByName("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleManagementService.getAuthoritiesByRoleName("MISSING"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Role not found");
    }

    @Test
    void getAllRoleNames_ShouldReturnList() {
        Role r1 = new Role(); r1.setName("A");
        Role r2 = new Role(); r2.setName("B");
        when(roleRepository.findAll()).thenReturn(java.util.List.of(r1, r2));

        assertThat(roleManagementService.getAllRoleNames()).containsExactly("A", "B");
    }

    @Test
    @DisplayName("Security Guardrail: Non-SYSTEM role cannot assign system authorities")
    void saveRoleMapping_ShouldThrowException_WhenNonSystemBankAssignsSystemAuthorities() {
        String roleName = "MALICIOUS_ROLE";
        Set<String> authorities = Set.of("catalog:product:read", "system:bank:write");

        assertThatThrownBy(() -> roleManagementService.saveRoleMapping(roleName, authorities))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("Only SYSTEM bank admins can assign system:* authorities.");
    }

    @Test
    @DisplayName("Security Guardrail: SYSTEM can assign system authorities")
    void saveRoleMapping_ShouldAllowSystemAuth_WhenBankIdIsSystem() {
        TenantContextHolder.setBankId("SYSTEM");
        String roleName = "SUPER_ADMIN";
        Set<String> authorities = Set.of("system:bank:write");
        when(roleRepository.findByName("SUPER_ADMIN")).thenReturn(Optional.empty());

        roleManagementService.saveRoleMapping(roleName, authorities);
        verify(roleRepository).save(any(Role.class));
    }
}