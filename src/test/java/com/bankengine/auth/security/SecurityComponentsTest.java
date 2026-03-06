package com.bankengine.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class SecurityComponentsTest {

    @Test
    void customAuthenticationEntryPoint_ShouldWriteJsonResponse() throws IOException {
        CustomAuthenticationEntryPoint entryPoint = new CustomAuthenticationEntryPoint();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AuthenticationException authException = mock(AuthenticationException.class);
        when(authException.getMessage()).thenReturn("Auth Failed");

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        entryPoint.commence(request, response, authException);

        verify(response).setContentType("application/json");
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        String result = stringWriter.toString();
        assertTrue(result.contains("401"));
        assertTrue(result.contains("Unauthorized"));
        assertTrue(result.contains("Auth Failed"));
    }

    @Test
    void customAccessDeniedHandler_ShouldWriteJsonResponse() throws IOException {
        CustomAccessDeniedHandler handler = new CustomAccessDeniedHandler();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AccessDeniedException accessDeniedException = new AccessDeniedException("Access Denied");

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        handler.handle(request, response, accessDeniedException);

        verify(response).setContentType("application/json");
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        String result = stringWriter.toString();
        assertTrue(result.contains("403"));
        assertTrue(result.contains("Forbidden"));
        assertTrue(result.contains("Access Denied"));
    }
}