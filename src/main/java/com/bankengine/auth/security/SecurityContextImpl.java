package com.bankengine.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextImpl implements SecurityContext {

    // Define the JWT claim key where the bank ID is stored
    private static final String BANK_ID_CLAIM = "bank_id"; // <-- You must configure your OAuth server to issue this claim

    @Override
    public String getCurrentBankId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("User is not authenticated or security context is empty.");
        }

        // The principal object for a validated OAuth2 Resource Server request is usually a Jwt object
        if (authentication.getPrincipal() instanceof Jwt jwt) {

            // 1. Attempt to get the Bank ID from the custom claim
            String bankId = jwt.getClaimAsString(BANK_ID_CLAIM);

            if (bankId == null) {
                // 2. Fallback check: Sometimes the audience (aud) is used as a tenant/bank ID
                bankId = jwt.getAudience().stream().findFirst().orElse(null);
            }

            if (bankId != null) {
                return bankId;
            }

            throw new IllegalStateException(
                    String.format("Authenticated principal (JWT) does not contain the required '%s' claim or a fallback audience claim.", BANK_ID_CLAIM));
        }

        throw new IllegalStateException("Authentication principal is not a JWT. Cannot determine Bank ID.");
    }
}