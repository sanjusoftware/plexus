package com.bankengine.auth.security;

import com.bankengine.auth.service.AuthorityMappingService;
import com.bankengine.common.model.BankConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private AuthorityMappingService authorityMappingService;

    private CustomOAuth2UserService customOAuth2UserService;

    @BeforeEach
    void setUp() {
        customOAuth2UserService = new CustomOAuth2UserService(authorityMappingService);
    }

    @Test
    void enrichUser_ShouldAddBankMetadataToAttributes() {
        // Arrange
        String issuerUri = "http://issuer";
        String userNameAttribute = "sub";
        String expectedPrincipalName = "user123";
        String clientId = "client";

        ClientRegistration registration = ClientRegistration.withRegistrationId("test")
                .clientId(clientId)
                .tokenUri("http://token")
                .authorizationUri("http://auth")
                .issuerUri(issuerUri)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://redirect")
                .userNameAttributeName(userNameAttribute)
                .build();

        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "val", Instant.now(), Instant.now().plusSeconds(60));
        OAuth2UserRequest request = new OAuth2UserRequest(registration, token);

        OAuth2User rawBaseUser = new DefaultOAuth2User(
                Collections.emptyList(),
                Map.of("sub", expectedPrincipalName, "name", "Test User", "azp", clientId),
                userNameAttribute
        );

        BankConfiguration config = BankConfiguration.builder()
                .bankId("B1")
                .name("Bank One")
                .build();

        // Stub the mapping service
        when(authorityMappingService.mapAuthoritiesWithContext(anyMap(), eq(issuerUri)))
                .thenReturn(new AuthorityMappingService.MappingResult(Collections.emptyList(), config));

        OAuth2User result = customOAuth2UserService.enrichUser(request, rawBaseUser);

        assertNotNull(result, "Resulting OAuth2User should not be null");
        assertEquals(expectedPrincipalName, result.getName());
        assertEquals("B1", result.getAttribute("bank_id"), "Bank ID should be enriched");
        assertEquals("Bank One", result.getAttribute("bankName"), "Bank Name should be enriched");
        assertTrue(result.getAttributes().containsKey("permissions"), "Permissions should be present");

        verify(authorityMappingService).mapAuthoritiesWithContext(anyMap(), eq(issuerUri));
    }

    @Test
    void enrichUser_ShouldHandleNullUserNameAttributeName() {
        // Arrange
        String issuerUri = "http://issuer";
        String clientId = "client";

        // Create a ClientRegistration with a null userInfoEndpoint.userNameAttributeName
        ClientRegistration registration = ClientRegistration.withRegistrationId("test")
                .clientId(clientId)
                .tokenUri("http://token")
                .authorizationUri("http://auth")
                .issuerUri(issuerUri)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://redirect")
                // We don't set userNameAttributeName here, or we'd have to use reflection to null it if the builder prevents it
                .build();

        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "val", Instant.now(), Instant.now().plusSeconds(60));
        OAuth2UserRequest request = new OAuth2UserRequest(registration, token);

        OAuth2User rawBaseUser = new DefaultOAuth2User(
                Collections.emptyList(),
                Map.of("sub", "user123", "name", "Test User", "azp", clientId),
                "sub"
        );

        BankConfiguration config = BankConfiguration.builder()
                .bankId("B1")
                .name("Bank One")
                .build();

        when(authorityMappingService.mapAuthoritiesWithContext(anyMap(), eq(issuerUri)))
                .thenReturn(new AuthorityMappingService.MappingResult(Collections.emptyList(), config));

        OAuth2User result = customOAuth2UserService.enrichUser(request, rawBaseUser);

        assertNotNull(result);
        assertEquals("user123", result.getName());
    }
}