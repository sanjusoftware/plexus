package com.bankengine.auth.service;

import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.BankStatus;
import com.bankengine.common.repository.BankConfigurationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorityMappingServiceTest {

    @Mock
    private PermissionMappingService permissionMappingService;

    @Mock
    private BankConfigurationRepository bankConfigurationRepository;

    @InjectMocks
    private AuthorityMappingService service;

    @Test
    void testMapAuthoritiesWithContext_Success() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", List.of("ROLE1"));
        claims.put("azp", "client1");
        String issuer = "http://issuer";

        BankConfiguration config = new BankConfiguration();
        config.setBankId("BANK1");
        config.setStatus(BankStatus.ACTIVE);

        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered("http://issuer", "client1")).thenReturn(Optional.of(config));
        when(permissionMappingService.getPermissionsForRoles(any())).thenReturn(Set.of("PERM1"));

        AuthorityMappingService.MappingResult result = service.mapAuthoritiesWithContext(claims, issuer);

        assertEquals("BANK1", result.bankConfig().getBankId());
        assertTrue(result.authorities().stream().anyMatch(a -> a.getAuthority().equals("PERM1")));
    }

    @Test
    void testMapAuthoritiesWithContext_IssuerMissing() {
        assertThrows(OAuth2AuthenticationException.class, () -> service.mapAuthoritiesWithContext(Map.of(), null));
        assertThrows(OAuth2AuthenticationException.class, () -> service.mapAuthoritiesWithContext(Map.of(), " "));
    }

    @Test
    void testMapAuthoritiesWithContext_ClientIdMissing() {
        assertThrows(OAuth2AuthenticationException.class, () -> service.mapAuthoritiesWithContext(Map.of(), "iss"));
    }

    @Test
    void testMapAuthoritiesWithContext_BankInactive() {
        Map<String, Object> claims = Map.of("azp", "client1");
        BankConfiguration config = new BankConfiguration();
        config.setBankId("BANK1");
        config.setStatus(BankStatus.INACTIVE);

        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(any(), any())).thenReturn(Optional.of(config));

        assertThrows(OAuth2AuthenticationException.class, () -> service.mapAuthoritiesWithContext(claims, "iss"));
    }

    @Test
    void testMapAuthoritiesWithContext_NoPermissions() {
        Map<String, Object> claims = Map.of("azp", "client1", "roles", "ROLE1");
        BankConfiguration config = new BankConfiguration();
        config.setBankId("BANK1");
        config.setStatus(BankStatus.ACTIVE);

        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(any(), any())).thenReturn(Optional.of(config));
        when(permissionMappingService.getPermissionsForRoles(any())).thenReturn(Collections.emptySet());

        assertThrows(OAuth2AuthenticationException.class, () -> service.mapAuthoritiesWithContext(claims, "iss"));
    }

    @Test
    void testExtractClientId_AudCases() {
        // String aud
        Map<String, Object> claims1 = Map.of("aud", "client1");
        // Handled via extractClientId private method called in mapAuthoritiesWithContext
        // We need a full valid flow to test branches in extractClientId

        BankConfiguration config = new BankConfiguration();
        config.setBankId("BANK1");
        config.setStatus(BankStatus.ACTIVE);
        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(any(), any())).thenReturn(Optional.of(config));
        when(permissionMappingService.getPermissionsForRoles(any())).thenReturn(Set.of("P1"));

        service.mapAuthoritiesWithContext(claims1, "iss");
        verify(bankConfigurationRepository).findByIssuerUrlAndClientIdUnfiltered(any(), eq("client1"));

        // List aud
        Map<String, Object> claims2 = Map.of("aud", List.of("client2"));
        service.mapAuthoritiesWithContext(claims2, "iss");
        verify(bankConfigurationRepository).findByIssuerUrlAndClientIdUnfiltered(any(), eq("client2"));
    }
}
