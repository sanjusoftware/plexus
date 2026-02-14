package com.bankengine.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextImpl implements SecurityContext {
    private static final String BANK_ID_CLAIM = "bank_id";

    @Override
    public String getCurrentBankId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("User is not authenticated or security context is empty.");
        }

        if (authentication.getPrincipal() instanceof Jwt jwt) {

            // 1. Attempt to get the Bank ID from the custom claim
            String bankId = jwt.getClaimAsString(BANK_ID_CLAIM);

            if (bankId == null) {
                // 2. Fallback check
                bankId = jwt.getAudience().stream().findFirst().orElse(null);
            }

            if (bankId != null) {
                return bankId;
            }

            throw new IllegalStateException("Authenticated principal (JWT) does not contain bank_id");
        }

        throw new IllegalStateException("Authentication principal is not a JWT.");
    }
}