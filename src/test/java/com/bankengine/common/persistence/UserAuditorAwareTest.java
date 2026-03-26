package com.bankengine.common.persistence;

import com.bankengine.auth.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class UserAuditorAwareTest {

    private UserAuditorAware userAuditorAware;

    @BeforeEach
    void setUp() {
        userAuditorAware = new UserAuditorAware();
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void shouldReturnSystemWhenInSystemMode() {
        TenantContextHolder.setSystemMode(true);
        Optional<String> result = userAuditorAware.getCurrentAuditor();
        assertThat(result).contains("SYSTEM");
    }

    @Test
    void shouldReturnEmptyWhenNotAuthenticated() {
        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(null);

        Optional<String> result = userAuditorAware.getCurrentAuditor();
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnPreferredUsernameFromOAuth2User() {
        String email = "sanjeev.cashier@example.com";
        OAuth2User principal = Mockito.mock(OAuth2User.class);
        when(principal.getAttributes()).thenReturn(Map.of("preferred_username", email));

        Authentication authentication = Mockito.mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);

        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        Optional<String> result = userAuditorAware.getCurrentAuditor();
        assertThat(result).contains(email);
    }

    @Test
    void shouldReturnEmailFromOAuth2UserWhenPreferredUsernameMissing() {
        String email = "sanjeev.cashier@example.com";
        OAuth2User principal = Mockito.mock(OAuth2User.class);
        when(principal.getAttributes()).thenReturn(Map.of("email", email));

        Authentication authentication = Mockito.mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);

        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        Optional<String> result = userAuditorAware.getCurrentAuditor();
        assertThat(result).contains(email);
    }

    @Test
    void shouldReturnNameFromOAuth2UserWhenEmailMissing() {
        String sub = "9-_h6fIAPSs98Rx_zUKhcgKkQFhoW3bqfua-BZq3RXA";
        OAuth2User principal = Mockito.mock(OAuth2User.class);
        when(principal.getAttributes()).thenReturn(Map.of());

        Authentication authentication = Mockito.mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(authentication.getName()).thenReturn(sub);

        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        Optional<String> result = userAuditorAware.getCurrentAuditor();
        assertThat(result).contains(sub);
    }

    @Test
    void shouldReturnPreferredUsernameFromJwt() {
        String email = "sanjeev.cashier@example.com";
        Jwt principal = Mockito.mock(Jwt.class);
        when(principal.getClaims()).thenReturn(Map.of("preferred_username", email));

        Authentication authentication = Mockito.mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);

        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        Optional<String> result = userAuditorAware.getCurrentAuditor();
        assertThat(result).contains(email);
    }

    @Test
    void shouldReturnNameWhenPrincipalIsString() {
        String name = "some-user";
        Authentication authentication = Mockito.mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(name);
        when(authentication.getName()).thenReturn(name);

        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        Optional<String> result = userAuditorAware.getCurrentAuditor();
        assertThat(result).contains(name);
    }
}
