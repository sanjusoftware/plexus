package com.bankengine.common.persistence;

import com.bankengine.auth.security.TenantContextHolder;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class UserAuditorAware implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        // 1. Check if we are in System Mode (e.g., TestDataSeeder)
        if (TenantContextHolder.isSystemMode()) {
            return Optional.of("SYSTEM");
        }

        // 2. Otherwise, look for the authenticated user from the SecurityContext
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof OAuth2User oauth2User) {
            return Optional.ofNullable(extractEmailFromAttributes(oauth2User.getAttributes()))
                    .or(() -> Optional.of(authentication.getName()));
        }

        if (principal instanceof Jwt jwt) {
            return Optional.ofNullable(extractEmailFromAttributes(jwt.getClaims()))
                    .or(() -> Optional.of(authentication.getName()));
        }

        return Optional.of(authentication.getName());
    }

    private String extractEmailFromAttributes(Map<String, Object> attributes) {
        if (attributes == null) return null;

        if (attributes.get("preferred_username") != null) {
            return attributes.get("preferred_username").toString();
        }
        if (attributes.get("email") != null) {
            return attributes.get("email").toString();
        }
        return null;
    }
}
