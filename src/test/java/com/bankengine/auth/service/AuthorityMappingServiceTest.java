package com.bankengine.auth.service;

import com.bankengine.auth.security.TenantContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class AuthorityMappingServiceTest {

    @Mock
    private PermissionMappingService permissionMappingService;

    private AuthorityMappingService authorityMappingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authorityMappingService = new AuthorityMappingService(permissionMappingService);
        TenantContextHolder.clear();
    }

    @Test
    void mapAuthorities_ShouldSucceed_WhenRolesExist() {
        String bankId = "HDFC_BANK_LTD";
        Map<String, Object> claims = Map.of(
                "sub", "user-123",
                "roles", List.of("BANK_ADMIN")
        );

        when(permissionMappingService.getPermissionsForRoles(List.of("BANK_ADMIN")))
                .thenReturn(Set.of("system:bank:read", "system:bank:write"));
        var authorities = authorityMappingService.mapAuthorities(claims, bankId);
        assertNotNull(authorities);
        assertEquals(2, authorities.size());
        assertEquals(bankId, TenantContextHolder.getBankId());
        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("system:bank:read")));
    }

    @Test
    void mapAuthorities_ShouldReturnEmpty_WhenNoRolesInClaims() {
        String bankId = "ICICI_BANK";
        Map<String, Object> claims = Map.of("sub", "user-456"); // No roles claim
        var authorities = authorityMappingService.mapAuthorities(claims, bankId);
        assertTrue(authorities.isEmpty());
        assertEquals(bankId, TenantContextHolder.getBankId());
    }

    @Test
    void mapAuthorities_ShouldHandleEmptyRoleList() {
        String bankId = "AXIS_BANK";
        Map<String, Object> claims = Map.of(
                "sub", "user-789",
                "roles", Collections.emptyList()
        );
        var authorities = authorityMappingService.mapAuthorities(claims, bankId);
        assertTrue(authorities.isEmpty());
    }
}
