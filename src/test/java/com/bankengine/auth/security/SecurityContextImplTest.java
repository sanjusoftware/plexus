package com.bankengine.auth.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityContextImplTest {

    private final SecurityContextImpl securityContext = new SecurityContextImpl();

    @BeforeEach
    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentBankId_ShouldReturnBankId_WhenClaimExists() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("bank_id")).thenReturn("TEST_BANK");
        setupMockAuthentication(jwt);

        String bankId = securityContext.getCurrentBankId();

        assertThat(bankId).isEqualTo("TEST_BANK");
    }

    @Test
    void getCurrentBankId_ShouldFallbackToAudience_WhenBankIdClaimMissing() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("bank_id")).thenReturn(null);
        when(jwt.getAudience()).thenReturn(List.of("AUD_BANK_ID"));
        setupMockAuthentication(jwt);

        String bankId = securityContext.getCurrentBankId();

        assertThat(bankId).isEqualTo("AUD_BANK_ID");
    }

    @Test
    void getCurrentBankId_ShouldThrow_WhenNoAuthentication() {
        // Context is empty by default
        assertThatThrownBy(securityContext::getCurrentBankId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User is not authenticated");
    }

    @Test
    void getCurrentBankId_ShouldThrow_WhenNotAuthenticatedFlagIsFalse() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThatThrownBy(securityContext::getCurrentBankId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User is not authenticated");
    }

    @Test
    void getCurrentBankId_ShouldThrow_WhenPrincipalNotJwt() {
        setupMockAuthentication("NotAJwtObject");

        assertThatThrownBy(securityContext::getCurrentBankId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("principal is not a JWT");
    }

    @Test
    void getCurrentBankId_ShouldThrow_WhenClaimsMissing() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("bank_id")).thenReturn(null);
        when(jwt.getAudience()).thenReturn(List.of()); // Empty audience
        setupMockAuthentication(jwt);

        assertThatThrownBy(securityContext::getCurrentBankId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Authenticated principal (JWT) does not contain bank_id");
    }

    private void setupMockAuthentication(Object principal) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}