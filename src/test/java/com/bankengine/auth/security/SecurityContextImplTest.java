package com.bankengine.auth.security;

import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.repository.BankConfigurationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;

import java.net.URL;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityContextImplTest {

    private final BankConfigurationRepository bankConfigurationRepository = mock(BankConfigurationRepository.class);
    private final SecurityContextImpl securityContext = new SecurityContextImpl(bankConfigurationRepository);

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
    void getCurrentBankId_ShouldThrow_WhenPrincipalUnsupported() {
        setupMockAuthentication("NotASupportedPrincipal");

        assertThatThrownBy(securityContext::getCurrentBankId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported authentication principal type");
    }

    @Test
    void getCurrentBankId_ShouldThrow_WhenClaimsAndIssuerMissing() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("bank_id")).thenReturn(null);
        when(jwt.getAudience()).thenReturn(List.of()); // Empty audience
        when(jwt.getIssuer()).thenReturn(null);
        setupMockAuthentication(jwt);

        assertThatThrownBy(securityContext::getCurrentBankId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Authenticated principal (JWT) does not contain bank_id");
    }

    @Test
    void getCurrentBankId_ShouldReturnBankId_FromOAuth2Token() {
        OAuth2AuthenticationToken auth = mock(OAuth2AuthenticationToken.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getAuthorizedClientRegistrationId()).thenReturn("OAUTH_BANK");
        SecurityContextHolder.getContext().setAuthentication(auth);

        String bankId = securityContext.getCurrentBankId();

        assertThat(bankId).isEqualTo("OAUTH_BANK");
    }

    @Test
    void getCurrentBankId_ShouldReturnBankId_FromOAuth2User() {
        OAuth2User user = mock(OAuth2User.class);
        when(user.getAttribute("bank_id")).thenReturn("USER_BANK");
        setupMockAuthentication(user);

        String bankId = securityContext.getCurrentBankId();

        assertThat(bankId).isEqualTo("USER_BANK");
    }

    @Test
    void getCurrentBankId_ShouldReturnBankId_FromOAuth2UserAudienceList() {
        OAuth2User user = mock(OAuth2User.class);
        when(user.getAttribute("bank_id")).thenReturn(null);
        when(user.getAttribute("aud")).thenReturn(List.of("AUD_LIST_BANK"));
        setupMockAuthentication(user);

        String bankId = securityContext.getCurrentBankId();

        assertThat(bankId).isEqualTo("AUD_LIST_BANK");
    }

    @Test
    void getCurrentBankId_ShouldReturnBankId_FromOAuth2UserAudienceString() {
        OAuth2User user = mock(OAuth2User.class);
        when(user.getAttribute("bank_id")).thenReturn(null);
        when(user.getAttribute("aud")).thenReturn("AUD_STRING_BANK");
        setupMockAuthentication(user);

        String bankId = securityContext.getCurrentBankId();

        assertThat(bankId).isEqualTo("AUD_STRING_BANK");
    }

    @Test
    void getCurrentBankId_ShouldThrow_WhenOAuth2UserClaimsMissing() {
        OAuth2User user = mock(OAuth2User.class);
        when(user.getAttribute("bank_id")).thenReturn(null);
        when(user.getAttribute("aud")).thenReturn(null);
        setupMockAuthentication(user);

        assertThatThrownBy(securityContext::getCurrentBankId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OAuth2User does not contain bank_id or audience attribute");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getCurrentBankId_ShouldFallbackToIssuer_WhenClaimsMissing() throws Exception {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("bank_id")).thenReturn(null);
        when(jwt.getAudience()).thenReturn(List.of());
        when(jwt.getIssuer()).thenReturn(new URL("https://idp.example.com/"));
        setupMockAuthentication(jwt);

        BankConfiguration config = BankConfiguration.builder()
                .bankId("ISSUER_BANK")
                .issuerUrl("https://idp.example.com")
                .build();
        when(bankConfigurationRepository.findAll()).thenReturn(List.of(config));

        String bankId = securityContext.getCurrentBankId();

        assertThat(bankId).isEqualTo("ISSUER_BANK");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getCurrentBankId_ShouldThrow_WhenIssuerUnknown() throws Exception {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("bank_id")).thenReturn(null);
        when(jwt.getAudience()).thenReturn(List.of());
        when(jwt.getIssuer()).thenReturn(new URL("https://unknown.com/"));
        setupMockAuthentication(jwt);

        when(bankConfigurationRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(securityContext::getCurrentBankId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No bank configuration found for issuer");
    }

    private void setupMockAuthentication(Object principal) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}