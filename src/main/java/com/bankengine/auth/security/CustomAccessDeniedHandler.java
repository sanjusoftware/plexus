package com.bankengine.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        String json = String.format(
                "{\"timestamp\": \"%s\", \"status\": 403, \"error\": \"Forbidden\", \"message\": \"Access Denied: %s\"}",
                LocalDateTime.now(), accessDeniedException.getMessage()
        );
        response.getWriter().write(json);
    }
}