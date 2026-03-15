package com.bankengine.auth.security;

import com.bankengine.common.repository.BankConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityContextImpl implements SecurityContext {
    private static final String BANK_ID_CLAIM = "bank_id";

    private final BankConfigurationRepository bankConfigurationRepository;

    @Override
    public String getCurrentBankId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("User is not authenticated or security context is empty.");
        }

        // Case 1: Bearer Token Flow (JWT)
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return resolveBankIdFromJwt(jwt);
        }

        // Case 2: Session-based Flow (OAuth2 Login)
        if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
            // In our system, the registrationId used in oauth2Login matches the bankId
            return oauth2Token.getAuthorizedClientRegistrationId();
        }

        // Case 3: Generic OAuth2User principal (if not captured by OAuth2AuthenticationToken)
        if (authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            return resolveBankIdFromOAuth2User(oauth2User);
        }

        throw new IllegalStateException("Unsupported authentication principal type: " +
                authentication.getPrincipal().getClass().getName() + ". Only JWT and OAuth2 session logins are supported.");
    }

    private String resolveBankIdFromJwt(Jwt jwt) {
        // 1. Attempt to get the Bank ID from the custom claim
        String bankId = jwt.getClaimAsString(BANK_ID_CLAIM);

        if (bankId != null) {
            return bankId;
        }

        // 2. Fallback: Check audience claim
        bankId = jwt.getAudience().stream().findFirst().orElse(null);
        if (bankId != null) {
            return bankId;
        }

        // 3. Last Resort: Identify bank by issuer URL from BankConfiguration
        String issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : null;
        if (issuer != null) {
            try {
                TenantContextHolder.setSystemMode(true);
                return bankConfigurationRepository.findAll().stream()
                        .filter(config -> issuer.replaceAll("/$", "").equalsIgnoreCase(config.getIssuerUrl().replaceAll("/$", "")))
                        .map(com.bankengine.common.model.BankConfiguration::getBankId)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No bank configuration found for issuer: " + issuer));
            } finally {
                TenantContextHolder.setSystemMode(false);
            }
        }

        throw new IllegalStateException("Authenticated principal (JWT) does not contain bank_id, valid audience, or recognizable issuer");
    }

    private String resolveBankIdFromOAuth2User(OAuth2User user) {
        // 1. Custom claim
        String bankId = user.getAttribute(BANK_ID_CLAIM);
        if (bankId != null) {
            return bankId;
        }

        // 2. Audience fallback (aud attribute is often a List or String in OAuth2User)
        Object aud = user.getAttribute("aud");
        if (aud instanceof java.util.Collection<?> col) {
            bankId = col.stream().findFirst().map(Object::toString).orElse(null);
        } else if (aud != null) {
            bankId = aud.toString();
        }

        if (bankId != null) {
            return bankId;
        }

        throw new IllegalStateException("OAuth2User does not contain bank_id or audience attribute");
    }
}