package com.bankengine.auth.service;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.BankStatus;
import com.bankengine.common.repository.BankConfigurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthorityMappingServiceTest {

    @Mock private PermissionMappingService permissionMappingService;
    @Mock private BankConfigurationRepository bankConfigurationRepository;
    private AuthorityMappingService authorityMappingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authorityMappingService = new AuthorityMappingService(permissionMappingService, bankConfigurationRepository);
        TenantContextHolder.clear();
    }

    @Test
    void mapAuthorities_ShouldSucceed_WhenRolesExist() {
        String issuer = "https://idp.com";
        String clientId = "client-123";
        Map<String, Object> claims = Map.of("sub", "user-1", "azp", clientId, "roles", List.of("ADMIN"));

        BankConfiguration config = BankConfiguration.builder().bankId("BANK-1").status(BankStatus.ACTIVE).build();
        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(issuer, clientId)).thenReturn(Optional.of(config));
        when(permissionMappingService.getPermissionsForRoles(any())).thenReturn(Set.of("perm-1"));

        var result = authorityMappingService.mapAuthoritiesWithContext(claims, issuer);

        assertFalse(result.authorities().isEmpty());
        assertEquals("BANK-1", result.bankConfig().getBankId());
        assertNull(TenantContextHolder.getBankId());
    }

    @Test
    void mapAuthorities_ShouldHandleTrailingSlashInIssuer() {
        String issuer = "https://idp.com/";
        String normalizedIssuer = "https://idp.com";
        String clientId = "client-123";
        Map<String, Object> claims = Map.of("sub", "user-1", "azp", clientId, "roles", List.of("ADMIN"));

        BankConfiguration config = BankConfiguration.builder().bankId("BANK-1").status(BankStatus.ACTIVE).build();
        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(normalizedIssuer, clientId)).thenReturn(Optional.of(config));
        when(permissionMappingService.getPermissionsForRoles(any())).thenReturn(Set.of("perm-1"));

        var result = authorityMappingService.mapAuthoritiesWithContext(claims, issuer);
        assertNotNull(result);
        assertEquals("BANK-1", result.bankConfig().getBankId());
    }

    @Test
    void mapAuthorities_ShouldThrow_WhenIssuerMissing() {
        assertThrows(OAuth2AuthenticationException.class, () -> authorityMappingService.mapAuthoritiesWithContext(Map.of(), null));
        assertThrows(OAuth2AuthenticationException.class, () -> authorityMappingService.mapAuthoritiesWithContext(Map.of(), ""));
    }

    @Test
    void mapAuthorities_ShouldThrow_WhenClientIdMissing() {
        assertThrows(OAuth2AuthenticationException.class, () -> authorityMappingService.mapAuthoritiesWithContext(Map.of(), "https://idp.com"));
        assertThrows(OAuth2AuthenticationException.class, () -> authorityMappingService.mapAuthoritiesWithContext(null, "https://idp.com"));
    }

    @Test
    void mapAuthorities_ShouldThrow_WhenBankNotFound() {
        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(anyString(), anyString())).thenReturn(Optional.empty());
        assertThrows(OAuth2AuthenticationException.class, () -> authorityMappingService.mapAuthoritiesWithContext(Map.of("azp", "c"), "https://i.com"));
    }

    @Test
    void mapAuthorities_ShouldThrow_WhenBankNotActive() {
        BankConfiguration config = BankConfiguration.builder().bankId("B").status(BankStatus.DRAFT).build();
        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(anyString(), anyString())).thenReturn(Optional.of(config));
        assertThrows(OAuth2AuthenticationException.class, () -> authorityMappingService.mapAuthoritiesWithContext(Map.of("azp", "c"), "https://i.com"));
    }

    @Test
    void mapAuthorities_ShouldThrow_WhenNoPermissions() {
        BankConfiguration config = BankConfiguration.builder().bankId("B").status(BankStatus.ACTIVE).build();
        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(anyString(), anyString())).thenReturn(Optional.of(config));
        when(permissionMappingService.getPermissionsForRoles(any())).thenReturn(Collections.emptySet());
        assertThrows(OAuth2AuthenticationException.class, () -> authorityMappingService.mapAuthoritiesWithContext(Map.of("azp", "c"), "https://i.com"));
    }

    @Test
    void extractClientId_ShouldHandleVariousAudienceFormats() {
        String issuer = "https://idp.com";
        BankConfiguration config = BankConfiguration.builder().bankId("B").status(BankStatus.ACTIVE).build();
        when(permissionMappingService.getPermissionsForRoles(any())).thenReturn(Set.of("p"));

        // azp takes precedence
        Map<String, Object> c1 = Map.of("azp", "client-azp", "aud", "client-aud");
        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(issuer, "client-azp")).thenReturn(Optional.of(config));
        assertNotNull(authorityMappingService.mapAuthoritiesWithContext(c1, issuer));

        // aud as string
        Map<String, Object> c2 = Map.of("aud", "client-aud");
        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(issuer, "client-aud")).thenReturn(Optional.of(config));
        assertNotNull(authorityMappingService.mapAuthoritiesWithContext(c2, issuer));

        // aud as list
        Map<String, Object> c3 = Map.of("aud", List.of("client-list-0", "other"));
        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(issuer, "client-list-0")).thenReturn(Optional.of(config));
        assertNotNull(authorityMappingService.mapAuthoritiesWithContext(c3, issuer));

        // aud as empty list
        Map<String, Object> c4 = Map.of("aud", List.of());
        assertThrows(OAuth2AuthenticationException.class, () -> authorityMappingService.mapAuthoritiesWithContext(c4, issuer));

        // aud as other type
        Map<String, Object> c5 = Map.of("aud", 123);
        assertThrows(OAuth2AuthenticationException.class, () -> authorityMappingService.mapAuthoritiesWithContext(c5, issuer));
    }

    @Test
    void extractRoles_ShouldHandleVariousFormats() {
        String issuer = "https://idp.com";
        BankConfiguration config = BankConfiguration.builder().bankId("B").status(BankStatus.ACTIVE).build();
        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(any(), any())).thenReturn(Optional.of(config));

        // roles as list with nulls
        List<String> rolesList = new ArrayList<>();
        rolesList.add("ROLE1");
        rolesList.add(null);
        Map<String, Object> c1 = Map.of("azp", "c", "roles", rolesList);
        when(permissionMappingService.getPermissionsForRoles(List.of("ROLE1"))).thenReturn(Set.of("p"));

        var result1 = authorityMappingService.mapAuthoritiesWithContext(c1, issuer);
        assertNotNull(result1);
        assertEquals(1, result1.authorities().size());

        // roles as single string
        Map<String, Object> c2 = Map.of("azp", "c", "roles", "ROLE2");
        when(permissionMappingService.getPermissionsForRoles(List.of("ROLE2"))).thenReturn(Set.of("p"));

        var result2 = authorityMappingService.mapAuthoritiesWithContext(c2, issuer);
        assertNotNull(result2);
        assertEquals(1, result2.authorities().size());
    }
}