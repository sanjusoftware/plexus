package com.bankengine.auth.security;

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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class JwtAuthConverterTest {

    @Mock private PermissionMappingService permissionMappingService;
    @Mock private BankConfigurationRepository bankConfigurationRepository;

    private JwtAuthConverter converter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        converter = new JwtAuthConverter(permissionMappingService, bankConfigurationRepository);
    }

    @Test
    void convert_ShouldSucceed_WhenBankIdAndIssuerMatch() {
        String bankId = "BANK_A";
        String issuer = "https://login.microsoftonline.com/tenant-a/v2.0";

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "user-123")
                .claim("bank_id", bankId)
                .claim("iss", issuer)
                .claim("roles", List.of("ADMIN"))
                .build();

        BankConfiguration config = new BankConfiguration();
        config.setBankId(bankId);
        config.setIssuerUrl(issuer);

        when(bankConfigurationRepository.findByBankId(bankId)).thenReturn(Optional.of(config));
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
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("bank_id", "BANK_A")
                .claim("iss", "https://hacker-issuer.com")
                .build();

        BankConfiguration config = new BankConfiguration();
        config.setBankId("BANK_A");
        config.setIssuerUrl("https://trusted-issuer.com");

        when(bankConfigurationRepository.findByBankId("BANK_A")).thenReturn(Optional.of(config));

        assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(jwt));
    }
}