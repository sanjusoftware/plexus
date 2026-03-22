package com.bankengine.auth.security;

import com.bankengine.auth.service.AuthorityMappingService;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.BankStatus;
import com.bankengine.common.repository.BankConfigurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomOidcUserServiceTest {

    @Mock private AuthorityMappingService authorityMappingService;
    @Mock private BankConfigurationRepository bankConfigurationRepository;

    private CustomOidcUserService customOidcUserService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Spy allows us to mock the super.loadUser call
        customOidcUserService = spy(new CustomOidcUserService(authorityMappingService, bankConfigurationRepository));
        TenantContextHolder.clear();
    }

    @Test
    void loadUser_ShouldSucceed_AndSetTenantContext() {
        // Arrange
        String issuer = "https://idp.com";
        String clientId = "client-123";
        String bankId = "BANK-HDFC";

        OidcUserRequest request = mockOidcRequest(clientId);
        OidcUser oidcUser = mockOidcUser(issuer);
        doReturn(oidcUser).when((OidcUserService) customOidcUserService).loadUser(request);

        BankConfiguration config = BankConfiguration.builder()
                .bankId(bankId)
                .issuerUrl(issuer)
                .clientId(clientId)
                .status(BankStatus.ACTIVE)
                .build();

        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(issuer, clientId))
                .thenReturn(Optional.of(config));

        // Use an answer to verify TenantContext is set DURING the call to mapAuthorities
        when(authorityMappingService.mapAuthorities(any(), eq(bankId)))
                .thenAnswer(invocation -> {
                    assertEquals(bankId, TenantContextHolder.getBankId(), "BankID must be set in context during mapping");
                    assertFalse(TenantContextHolder.isSystemMode(), "SystemMode must be disabled during mapping");
                    return List.of(new SimpleGrantedAuthority("ROLE_USER"));
                });

        // Act
        OidcUser result = customOidcUserService.loadUser(request);

        // Assert
        assertNotNull(result);
        assertEquals(bankId, result.getAttributes().get("bank_id"));
        assertNull(TenantContextHolder.getBankId(), "Context should be cleared after request");
    }

    @Test
    void loadUser_ShouldThrowException_WhenBankNotFound() {
        // Arrange
        String issuer = "https://unknown.com";
        String clientId = "unknown-client";

        OidcUserRequest request = mockOidcRequest(clientId);
        OidcUser oidcUser = mockOidcUser(issuer);
        doReturn(oidcUser).when((OidcUserService) customOidcUserService).loadUser(request);

        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(any(), any()))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(OAuth2AuthenticationException.class, () -> customOidcUserService.loadUser(request));
        assertFalse(TenantContextHolder.isSystemMode(), "System mode must be cleared even on failure");
    }

    @Test
    void loadUser_ShouldThrowException_WhenBankNotActive() {
        // Arrange
        String issuer = "https://idp.com";
        String clientId = "client-inactive";

        OidcUserRequest request = mockOidcRequest(clientId);
        OidcUser oidcUser = mockOidcUser(issuer);
        doReturn(oidcUser).when((OidcUserService) customOidcUserService).loadUser(request);

        BankConfiguration config = BankConfiguration.builder()
                .bankId("INACTIVE-BANK")
                .status(BankStatus.DRAFT) // Not ACTIVE
                .build();

        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(any(), any()))
                .thenReturn(Optional.of(config));

        // Act & Assert
        OAuth2AuthenticationException ex = assertThrows(OAuth2AuthenticationException.class,
                () -> customOidcUserService.loadUser(request));
        assertTrue(ex.getMessage().contains("not active"));
    }

    @Test
    void loadUser_ShouldThrowException_WhenNoAuthoritiesMapped() {
        // Arrange
        String issuer = "https://idp.com";
        String clientId = "client-123";
        String bankId = "BANK-1";

        OidcUserRequest request = mockOidcRequest(clientId);
        OidcUser oidcUser = mockOidcUser(issuer);
        doReturn(oidcUser).when((OidcUserService) customOidcUserService).loadUser(request);

        BankConfiguration config = BankConfiguration.builder()
                .bankId(bankId)
                .status(BankStatus.ACTIVE)
                .build();

        when(bankConfigurationRepository.findByIssuerUrlAndClientIdUnfiltered(any(), any()))
                .thenReturn(Optional.of(config));

        // Simulate zero mapping (e.g., role exists in IDP but not for this bank in DB)
        when(authorityMappingService.mapAuthorities(any(), any())).thenReturn(Collections.emptyList());

        // Act & Assert
        assertThrows(OAuth2AuthenticationException.class, () -> customOidcUserService.loadUser(request));
    }

    // --- Helper Methods ---

    private OidcUserRequest mockOidcRequest(String clientId) {
        OidcUserRequest request = mock(OidcUserRequest.class);
        ClientRegistration registration = ClientRegistration.withRegistrationId("test")
                .clientId(clientId)
                .authorizationUri("http://auth")
                .tokenUri("http://token")
                .redirectUri("http://redirect")
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .build();
        when(request.getClientRegistration()).thenReturn(registration);
        return request;
    }

    private OidcUser mockOidcUser(String issuer) {
        Map<String, Object> claims = Map.of(
                "sub", "user-123",
                "iss", issuer,
                "name", "Test User",
                "roles", List.of("SYSTEM_ADMIN")
        );
        OidcIdToken idToken = new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(60), claims);
        return new DefaultOidcUser(Collections.emptyList(), idToken);
    }
}