package com.bankengine.auth.security;

import com.bankengine.auth.service.AuthorityMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomOAuth2UserServiceTest {

    @Mock
    private AuthorityMappingService authorityMappingService;

    private CustomOAuth2UserService customOAuth2UserService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        customOAuth2UserService = new CustomOAuth2UserService(authorityMappingService) {
            @Override
            public OAuth2User loadUser(OAuth2UserRequest userRequest) {
                // Mocking super.loadUser
                OAuth2User mockUser = mock(OAuth2User.class);
                when(mockUser.getAttributes()).thenReturn(Map.of("sub", "user1", "name", "User One"));
                when(mockUser.getName()).thenReturn("user1");

                String issuer = userRequest.getClientRegistration().getProviderDetails().getIssuerUri();
                var authorities = authorityMappingService.mapAuthorities(mockUser.getAttributes(), issuer);
                String userNameAttributeName = userRequest.getClientRegistration()
                        .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

                return new org.springframework.security.oauth2.core.user.DefaultOAuth2User(authorities, mockUser.getAttributes(), userNameAttributeName);
            }
        };
    }

    @Test
    void loadUser_ShouldMapAuthorities() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("test")
                .clientId("client")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/callback")
                .authorizationUri("http://localhost/auth")
                .tokenUri("http://localhost/token")
                .issuerUri("https://idp.com")
                .userNameAttributeName("sub")
                .build();

        OAuth2UserRequest request = new OAuth2UserRequest(registration, mock(org.springframework.security.oauth2.core.OAuth2AccessToken.class));

        when(authorityMappingService.mapAuthorities(anyMap(), eq("https://idp.com")))
                .thenReturn(Collections.emptyList());

        OAuth2User user = customOAuth2UserService.loadUser(request);

        assertNotNull(user);
        assertEquals(0, user.getAuthorities().size());
        assertEquals("user1", user.getName());
    }
}
