package com.bankengine.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class BankContextFilter extends OncePerRequestFilter {

    private final SecurityContext securityContext;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            // 1. Fetch the Bank ID from the current authenticated JWT principal
            String bankId = securityContext.getCurrentBankId();

            // 2. Set the Bank ID in the static ThreadLocal holder
            BankContextHolder.setBankId(bankId);

            filterChain.doFilter(request, response);
        } catch (IllegalStateException e) {
            // Log the failure, but still allow filter chain to proceed for public endpoints
            filterChain.doFilter(request, response);
        } finally {
            // CRITICAL: Always clear the context after the request completes
            BankContextHolder.clear();
        }
    }
}