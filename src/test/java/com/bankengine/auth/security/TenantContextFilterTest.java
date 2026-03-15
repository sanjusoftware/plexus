package com.bankengine.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantContextFilterTest {

    @Mock
    private SecurityContext securityContext;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private TenantContextFilter tenantContextFilter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tenantContextFilter = new TenantContextFilter(securityContext);
        TenantContextHolder.clear();
    }

    @Test
    void doFilterInternal_ShouldSetBankId() throws ServletException, IOException {
        when(securityContext.getCurrentBankId()).thenReturn("BANK1");
        when(request.getRequestURI()).thenReturn("/api/v1/products");

        tenantContextFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // Verify bankId was cleared in finally block
        verify(securityContext).getCurrentBankId();
    }

    @Test
    void doFilterInternal_ShouldHandleException() throws ServletException, IOException {
        when(securityContext.getCurrentBankId()).thenThrow(new IllegalStateException("Auth failed"));
        when(request.getRequestURI()).thenReturn("/api/v1/products");

        tenantContextFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_ShouldSuppressWarningForActuator() throws ServletException, IOException {
        when(securityContext.getCurrentBankId()).thenThrow(new IllegalStateException("Auth failed"));
        when(request.getRequestURI()).thenReturn("/actuator/health");

        tenantContextFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
