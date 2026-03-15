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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    void mapAuthorities_ShouldSucceed_UsingAzpClaim() {
        String issuer = "https://idp.com";
        String clientId = "client-123";
        String bankId = "BANK-HDFC";
        Map<String, Object> claims = Map.of(
                "iss", issuer,
                "azp", clientId,
                "roles", List.of("USER"),
                "bank_id", "MALICIOUS_BANK" // Should be ignored
        );

        BankConfiguration config = BankConfiguration.builder()
                .bankId(bankId)
                .issuerUrl(issuer)
                .clientId(clientId)
                .status(BankStatus.ACTIVE)
                .build();

        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(issuer, clientId))
                .thenReturn(Optional.of(config));
        when(permissionMappingService.getPermissionsForRoles(List.of("USER")))
                .thenReturn(Set.of("ROLE_USER"));

        var authorities = authorityMappingService.mapAuthorities(claims, issuer);

        assertNotNull(authorities);
        assertEquals(1, authorities.size());
        assertEquals(bankId, TenantContextHolder.getBankId());
        verify(bankConfigurationRepository).findByIssuerUrlAndClientIdUnfiltered(issuer, clientId);
    }

    @Test
    void mapAuthorities_ShouldSucceed_UsingAudClaimString() {
        String issuer = "https://idp.com";
        String clientId = "client-456";
        String bankId = "BANK-ICICI";
        Map<String, Object> claims = Map.of(
                "iss", issuer,
                "aud", clientId,
                "roles", List.of("ADMIN")
        );

        BankConfiguration config = BankConfiguration.builder()
                .bankId(bankId)
                .issuerUrl(issuer)
                .clientId(clientId)
                .status(BankStatus.ACTIVE)
                .build();

        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(issuer, clientId))
                .thenReturn(Optional.of(config));
        when(permissionMappingService.getPermissionsForRoles(List.of("ADMIN")))
                .thenReturn(Set.of("ROLE_ADMIN"));

        var authorities = authorityMappingService.mapAuthorities(claims, issuer);

        assertNotNull(authorities);
        assertEquals(bankId, TenantContextHolder.getBankId());
    }

    @Test
    void mapAuthorities_ShouldSucceed_UsingAudClaimList() {
        String issuer = "https://idp.com";
        String clientId = "client-789";
        String bankId = "BANK-AXIS";
        Map<String, Object> claims = Map.of(
                "iss", issuer,
                "aud", List.of(clientId, "other-aud"),
                "roles", List.of("MANAGER")
        );

        BankConfiguration config = BankConfiguration.builder()
                .bankId(bankId)
                .issuerUrl(issuer)
                .clientId(clientId)
                .status(BankStatus.ACTIVE)
                .build();

        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(issuer, clientId))
                .thenReturn(Optional.of(config));
        when(permissionMappingService.getPermissionsForRoles(List.of("MANAGER")))
                .thenReturn(Set.of("ROLE_MANAGER"));

        var authorities = authorityMappingService.mapAuthorities(claims, issuer);

        assertNotNull(authorities);
        assertEquals(bankId, TenantContextHolder.getBankId());
    }

    @Test
    void mapAuthorities_ShouldThrowException_WhenBankNotFound() {
        String issuer = "https://unknown.com";
        String clientId = "unknown-client";
        Map<String, Object> claims = Map.of(
                "iss", issuer,
                "azp", clientId
        );

        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(issuer, clientId))
                .thenReturn(Optional.empty());

        assertThrows(OAuth2AuthenticationException.class, () ->
            authorityMappingService.mapAuthorities(claims, issuer)
        );
    }

    @Test
    void mapAuthorities_ShouldThrowException_WhenBankNotActive() {
        String issuer = "https://idp.com";
        String clientId = "client-inactive";
        Map<String, Object> claims = Map.of(
                "iss", issuer,
                "azp", clientId
        );

        BankConfiguration config = BankConfiguration.builder()
                .bankId("INACTIVE-BANK")
                .issuerUrl(issuer)
                .clientId(clientId)
                .status(BankStatus.DRAFT)
                .build();

        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(issuer, clientId))
                .thenReturn(Optional.of(config));

        assertThrows(OAuth2AuthenticationException.class, () ->
            authorityMappingService.mapAuthorities(claims, issuer)
        );
    }

    @Test
    void mapAuthorities_ShouldNormalizeIssuerWithTrailingSlash() {
        String issuerWithSlash = "https://idp.com/";
        String normalizedIssuer = "https://idp.com";
        String clientId = "client-123";
        Map<String, Object> claims = Map.of(
                "iss", issuerWithSlash,
                "azp", clientId
        );

        BankConfiguration config = BankConfiguration.builder()
                .bankId("BANK-1")
                .issuerUrl(normalizedIssuer)
                .clientId(clientId)
                .status(BankStatus.ACTIVE)
                .build();

        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(normalizedIssuer, clientId))
                .thenReturn(Optional.of(config));

        authorityMappingService.mapAuthorities(claims, issuerWithSlash);

        verify(bankConfigurationRepository).findByIssuerUrlAndClientIdUnfiltered(normalizedIssuer, clientId);
    }
}
