package com.bankengine.auth.security;

import com.bankengine.auth.service.AuthorityMappingService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomOAuth2UserServiceTest {

    @Test
    void loadUser_ShouldWork() {
        AuthorityMappingService mappingService = mock(AuthorityMappingService.class);
        CustomOAuth2UserService service = new CustomOAuth2UserService(mappingService) {
            @Override
            public OAuth2User loadUser(OAuth2UserRequest userRequest) {
                OAuth2User user = mock(OAuth2User.class);
                when(user.getAttributes()).thenReturn(Map.of("sub", "user123", "name", "Test User"));
                when(user.getName()).thenReturn("Test User");
                return user;
            }
        };

        ClientRegistration registration = ClientRegistration.withRegistrationId("test")
                .clientId("client")
                .tokenUri("http://token")
                .authorizationUri("http://auth")
                .issuerUri("http://issuer")
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://redirect")
                .userNameAttributeName("sub")
                .build();

        OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "val", Instant.now(), Instant.now().plusSeconds(60));
        OAuth2UserRequest request = new OAuth2UserRequest(registration, token);

        when(mappingService.mapAuthorities(anyMap(), eq("http://issuer"))).thenReturn(Collections.emptyList());

        OAuth2User result = service.loadUser(request);

        assertNotNull(result);
        assertEquals("Test User", result.getName());
    }
}
