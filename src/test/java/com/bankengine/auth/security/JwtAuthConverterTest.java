package com.bankengine.auth.security;

import com.bankengine.auth.service.PermissionMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class JwtAuthConverterTest {

    @Mock private PermissionMappingService permissionMappingService;
    private JwtAuthConverter converter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        converter = new JwtAuthConverter(permissionMappingService);
    }

    @Test
    void convert_ShouldSucceed_WhenBankIdIsPresent() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-123")
                .claim("bank_id", "BANK_A")
                .claim("roles", List.of("ADMIN"))
                .build();

        when(permissionMappingService.getPermissionsForRoles(List.of("ADMIN")))
                .thenReturn(Set.of("READ_PRODUCT", "WRITE_PRODUCT"));

        var token = (JwtAuthenticationToken) converter.convert(jwt);

        assertNotNull(token);
        assertEquals("user-123", token.getName());
        assertTrue(token.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("READ_PRODUCT")));
    }

    @Test
    void convert_ShouldThrowException_WhenBankIdIsMissing() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-123")
                // bank_id is missing
                .build();

        assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(jwt));
    }
}