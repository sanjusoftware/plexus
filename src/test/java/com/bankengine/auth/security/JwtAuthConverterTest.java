package com.bankengine.auth.security;

import com.bankengine.auth.service.AuthorityMappingService;
import com.bankengine.common.model.BankConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthConverterTest {

    @Test
    void convert_WithNullIssuer_ShouldWork() {
        AuthorityMappingService mappingService = mock(AuthorityMappingService.class);
        BankConfiguration config = BankConfiguration.builder().bankId("SYSTEM").build();

        when(mappingService.mapAuthoritiesWithContext(anyMap(), isNull()))
                .thenReturn(new AuthorityMappingService.MappingResult(Collections.emptyList(), config));

        JwtAuthConverter converter = new JwtAuthConverter(mappingService);

        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("alg", "none"), Map.of("sub", "user123"));

        var result = converter.convert(jwt);

        assertNotNull(result);
        assertTrue(result instanceof JwtAuthenticationToken);
        assertEquals("user123", result.getName());
    }

    @Test
    void convert_WithIssuer_ShouldWork() {
        AuthorityMappingService mappingService = mock(AuthorityMappingService.class);
        BankConfiguration config = BankConfiguration.builder().bankId("BANK1").build();

        // Mock the new result DTO for specific issuer
        when(mappingService.mapAuthoritiesWithContext(anyMap(), eq("http://issuer")))
                .thenReturn(new AuthorityMappingService.MappingResult(Collections.emptyList(), config));

        JwtAuthConverter converter = new JwtAuthConverter(mappingService);

        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("alg", "none"), Map.of("sub", "user123", "iss", "http://issuer"));

        var result = converter.convert(jwt);

        assertNotNull(result);
        assertTrue(result instanceof JwtAuthenticationToken);
        assertEquals("user123", result.getName());
    }
}