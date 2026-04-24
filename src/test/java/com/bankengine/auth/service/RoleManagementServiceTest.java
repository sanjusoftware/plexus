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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.oauth2.jwt.Jwt.withTokenValue;

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
        when(roleRepository.findAll()).thenReturn(List.of(r1, r2));

        assertThat(roleManagementService.getAllRoleNames()).containsExactly("A", "B");
    }

    @Test
    @DisplayName("Security Guardrail: Non-SYSTEM role cannot assign system authorities")
    void saveRoleMapping_ShouldThrowException_WhenNonSystemBankAssignsSystemAuthorities() {
        String roleName = "MALICIOUS_ROLE";
        Set<String> authorities = Set.of("catalog:product:read", "system:bank:write");

        assertThatThrownBy(() -> roleManagementService.saveRoleMapping(roleName, authorities))
                .isInstanceOf(AccessDeniedException.class)
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

    @Test
    @DisplayName("Security Guardrail: User cannot delete own assigned role")
    void deleteRole_ShouldThrowException_WhenDeletingOwnRole() {
        Role existingRole = new Role();
        existingRole.setName("ADMIN");
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(existingRole));

        Jwt jwt = withTokenValue("mock-token")
                .header("alg", "none")
                .claim("roles", List.of("ADMIN", "OTHER_ROLE"))
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);

        assertThatThrownBy(() -> roleManagementService.deleteRole("ADMIN", authentication))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("cannot delete a role assigned to your own account");

        verify(roleRepository, never()).delete(any(Role.class));
        verify(permissionMappingService, never()).evictAllRolePermissionsCache();
    }

    @Test
    @DisplayName("Branch: deleteRole - system admin check")
    void testDeleteRole_systemAdmin() {
        Role role = new Role(); role.setName("SYSTEM_ADMIN");
        when(roleRepository.findByName("SYSTEM_ADMIN")).thenReturn(Optional.of(role));
        assertThrows(AccessDeniedException.class, () -> roleManagementService.deleteRole("SYSTEM_ADMIN", null));
    }

    @Test
    @DisplayName("Branch: extractCurrentUserRoles - various cases")
    void testExtractCurrentUserRoles() {
        Role role = new Role(); role.setName("TARGET");
        when(roleRepository.findByName("TARGET")).thenReturn(Optional.of(role));

        // null auth
        roleManagementService.deleteRole("TARGET", null);
        verify(roleRepository).delete(role);
        reset(roleRepository);
        when(roleRepository.findByName("TARGET")).thenReturn(Optional.of(role));

        // JwtAuthenticationToken with null claims
        JwtAuthenticationToken jwtAuthNull = mock(JwtAuthenticationToken.class);
        when(jwtAuthNull.getTokenAttributes()).thenReturn(null);
        roleManagementService.deleteRole("TARGET", jwtAuthNull);
        verify(roleRepository).delete(role);
        reset(roleRepository);
        when(roleRepository.findByName("TARGET")).thenReturn(Optional.of(role));

        // OAuth2User
        OAuth2User oauth2User = mock(OAuth2User.class);
        when(oauth2User.getAttributes()).thenReturn(Map.of("roles", List.of("TARGET")));
        Authentication auth1 = mock(Authentication.class);
        when(auth1.getPrincipal()).thenReturn(oauth2User);
        assertThrows(AccessDeniedException.class, () -> roleManagementService.deleteRole("TARGET", auth1));

        // Jwt
        Jwt jwt = withTokenValue("token").header("alg", "none").claim("roles", "TARGET").build();
        Authentication auth2 = mock(Authentication.class);
        when(auth2.getPrincipal()).thenReturn(jwt);
        assertThrows(AccessDeniedException.class, () -> roleManagementService.deleteRole("TARGET", auth2));

        // Map
        Map<String, Object> principalMap = Map.of("roles", List.of("TARGET"));
        Authentication auth3 = mock(Authentication.class);
        when(auth3.getPrincipal()).thenReturn(principalMap);
        assertThrows(AccessDeniedException.class, () -> roleManagementService.deleteRole("TARGET", auth3));

        // Single string role in map
        Map<String, Object> principalMapSingle = Map.of("roles", "TARGET");
        Authentication auth4 = mock(Authentication.class);
        when(auth4.getPrincipal()).thenReturn(principalMapSingle);
        assertThrows(AccessDeniedException.class, () -> roleManagementService.deleteRole("TARGET", auth4));
    }

    @Test
    @DisplayName("Branch: getAllRoleMappings success")
    void testGetAllRoleMappings() {
        when(roleRepository.findAll()).thenReturn(List.of(new Role()));
        assertFalse(roleManagementService.getAllRoleMappings().isEmpty());
    }
}
