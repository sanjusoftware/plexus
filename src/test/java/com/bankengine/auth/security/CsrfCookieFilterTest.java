package com.bankengine.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.CsrfToken;

import java.io.IOException;

import static org.mockito.Mockito.*;

class CsrfCookieFilterTest {

    @Test
    void doFilterInternal_WithNullToken_ShouldProceed() throws ServletException, IOException {
        CsrfCookieFilter filter = new CsrfCookieFilter();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        when(request.getAttribute(CsrfToken.class.getName())).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_WithToken_ShouldPopulate() throws ServletException, IOException {
        CsrfCookieFilter filter = new CsrfCookieFilter();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        CsrfToken csrfToken = mock(CsrfToken.class);

        when(request.getAttribute(CsrfToken.class.getName())).thenReturn(csrfToken);

        filter.doFilterInternal(request, response, filterChain);

        verify(csrfToken).getToken();
        verify(filterChain).doFilter(request, response);
    }
}
