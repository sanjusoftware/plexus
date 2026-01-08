package com.bankengine.common.persistence;

import com.bankengine.auth.security.BankContextHolder;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UserAuditorAware implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        // 1. Check if we are in System Mode (e.g., TestDataSeeder)
        if (BankContextHolder.isSystemMode()) {
            return Optional.of("SYSTEM");
        }

        // 2. Otherwise, look for the authenticated user from the JWT
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName); // This is the 'sub' claim from JWT
    }
}