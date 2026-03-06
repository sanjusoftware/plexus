package com.bankengine.auth.config;

import com.bankengine.auth.security.CustomAccessDeniedHandler;
import com.bankengine.auth.security.CustomAuthenticationEntryPoint;
import com.bankengine.auth.security.JwtAuthConverter;
import com.bankengine.auth.security.TenantContextFilter;
import com.bankengine.common.repository.BankConfigurationRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityConfigTest {

    @Mock private JwtAuthConverter jwtAuthConverter;
    @Mock private TenantContextFilter tenantContextFilter;
    @Mock private BankConfigurationRepository bankConfigurationRepository;
    @Mock private CustomAuthenticationEntryPoint authenticationEntryPoint;
    @Mock private CustomAccessDeniedHandler accessDeniedHandler;

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        securityConfig = new SecurityConfig(jwtAuthConverter, tenantContextFilter, bankConfigurationRepository, authenticationEntryPoint, accessDeniedHandler);
    }

    @Test
    void tenantAuthenticationManagerResolver_ShouldThrowException_WhenNoToken() {
        AuthenticationManagerResolver<HttpServletRequest> resolver = securityConfig.tenantAuthenticationManagerResolver();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);

        assertThrows(OAuth2AuthenticationException.class, () -> resolver.resolve(request));
    }

    @Test
    void tenantAuthenticationManagerResolver_ShouldThrowException_WhenInvalidTokenFormat() {
        AuthenticationManagerResolver<HttpServletRequest> resolver = securityConfig.tenantAuthenticationManagerResolver();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");

        OAuth2AuthenticationException ex = assertThrows(OAuth2AuthenticationException.class, () -> resolver.resolve(request));
        assertEquals("invalid_token", ex.getError().getErrorCode());
    }
}
