package com.bankengine.auth.security;

import com.bankengine.auth.service.AuthorityMappingService;
import com.bankengine.auth.service.PermissionMappingService;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.repository.BankConfigurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class JwtAuthConverterTest {

    @Mock private PermissionMappingService permissionMappingService;
    @Mock private BankConfigurationRepository bankConfigurationRepository;
    private AuthorityMappingService authorityMappingService;

    private JwtAuthConverter converter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authorityMappingService = new AuthorityMappingService(permissionMappingService, bankConfigurationRepository);
        converter = new JwtAuthConverter(authorityMappingService);
    }

    @Test
    void convert_ShouldThrowException_WhenBankNotActive() {
        String bankId = "BANK_B";
        String issuer = "https://trusted.com";
        String clientId = "client-b";

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("azp", clientId)
                .claim("iss", issuer)
                .build();

        BankConfiguration config = new BankConfiguration();
        config.setBankId(bankId);
        config.setIssuerUrl(issuer);
        config.setClientId(clientId);
        config.setStatus(com.bankengine.common.model.BankStatus.DRAFT); // NOT ACTIVE

        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(issuer, clientId))
                .thenReturn(Optional.of(config));

        assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(jwt));
    }

    @Test
    void convert_ShouldSucceed_WhenIssuerAndClientIdMatch() {
        String bankId = "BANK_A";
        String issuer = "https://login.microsoftonline.com/tenant-a/v2.0";
        String clientId = "client-a";

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "user-123")
                .claim("azp", clientId)
                .claim("iss", issuer)
                .claim("roles", List.of("ADMIN"))
                .build();

        BankConfiguration config = new BankConfiguration();
        config.setBankId(bankId);
        config.setIssuerUrl(issuer);
        config.setClientId(clientId);
        config.setStatus(com.bankengine.common.model.BankStatus.ACTIVE);

        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(issuer, clientId))
                .thenReturn(Optional.of(config));
        when(permissionMappingService.getPermissionsForRoles(List.of("ADMIN")))
                .thenReturn(Set.of("READ_PRODUCT"));

        var token = (JwtAuthenticationToken) converter.convert(jwt);

        assertNotNull(token);
        assertEquals("user-123", token.getName());
        assertTrue(token.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("READ_PRODUCT")));
        assertEquals(bankId, TenantContextHolder.getBankId());
    }

    @Test
    void convert_ShouldThrowException_WhenIssuerMismatches() {
        String issuer = "https://hacker-issuer.com";
        String clientId = "client-a";

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("azp", clientId)
                .claim("iss", issuer)
                .build();

        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(issuer, clientId))
                .thenReturn(Optional.empty());

        assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(jwt));
    }

    @Test
    void convert_ShouldThrowException_WhenIssuerIsNull() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("azp", "client-a")
                // No 'iss' claim
                .build();

        assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(jwt));
    }

    @Test
    void convert_ShouldThrowException_WhenClientIdIsNull() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("iss", "https://trusted.com")
                // No 'azp' or 'aud' claim
                .build();

        assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(jwt));
    }

    @Test
    void convert_ShouldThrowException_WhenBankMissing() {
        String issuer = "https://trusted.com";
        String clientId = "MISSING_CLIENT";

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("azp", clientId)
                .claim("iss", issuer)
                .build();

        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(issuer, clientId))
                .thenReturn(Optional.empty());

        assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(jwt));
    }

    @Test
    void convert_ShouldHandleTrailingSlashInIssuer() {
        String bankId = "BANK_A";
        String issuerWithSlash = "https://trusted.com/";
        String normalizedIssuer = "https://trusted.com";
        String clientId = "client-a";

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "user-123")
                .claim("azp", clientId)
                .claim("iss", issuerWithSlash)
                .build();

        BankConfiguration config = new BankConfiguration();
        config.setBankId(bankId);
        config.setIssuerUrl(normalizedIssuer);
        config.setClientId(clientId);
        config.setStatus(com.bankengine.common.model.BankStatus.ACTIVE);

        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(normalizedIssuer, clientId))
                .thenReturn(Optional.of(config));

        var token = converter.convert(jwt);
        assertNotNull(token);
    }

    @Test
    void convert_ShouldHandleMissingRoles() {
        String issuer = "https://trusted.com";
        String clientId = "client-a";

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("azp", clientId)
                .claim("iss", issuer)
                .claim("sub", "user-123")
                // Explicitly NO 'roles' claim
                .build();

        BankConfiguration config = new BankConfiguration();
        config.setIssuerUrl(issuer);
        config.setClientId(clientId);
        config.setBankId("BANK_A");
        config.setStatus(com.bankengine.common.model.BankStatus.ACTIVE);

        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(issuer, clientId))
                .thenReturn(Optional.of(config));

        var token = converter.convert(jwt);

        assertNotNull(token, "Converter should not return null");
        assertTrue(token.getAuthorities().isEmpty(), "Authorities should be empty when roles claim is missing");
    }

    @Test
    void convert_ShouldHandleEmptyRoles() {
        String issuer = "https://trusted.com";
        String clientId = "client-a";

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("azp", clientId)
                .claim("iss", issuer)
                .claim("sub", "user-123")
                .claim("roles", List.of())
                .build();

        BankConfiguration config = new BankConfiguration();
        config.setIssuerUrl(issuer);
        config.setClientId(clientId);
        config.setBankId("BANK_A");
        config.setStatus(com.bankengine.common.model.BankStatus.ACTIVE);

        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(issuer, clientId))
                .thenReturn(Optional.of(config));
        when(permissionMappingService.getPermissionsForRoles(List.of())).thenReturn(Set.of());

        var token = converter.convert(jwt);

        assertNotNull(token);
        assertTrue(token.getAuthorities().isEmpty());
    }
}