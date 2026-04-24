package com.bankengine.auth.service;

import com.bankengine.common.model.BankConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionAuthorityRefreshServiceTest {

    @Mock
    private AuthorityMappingService authorityMappingService;

    @InjectMocks
    private SessionAuthorityRefreshService service;

    @Test
    void refresh_NonOAuthToken_ShouldReturnEarly() {
        Authentication auth = mock(Authentication.class);
        service.refreshCurrentSessionAuthorities(auth, "ROLE_A", null);
        verifyNoInteractions(authorityMappingService);
    }

    @Test
    void refresh_NoMatchingRole_ShouldReturnEarly() {
        OAuth2User principal = mock(OAuth2User.class);
        when(principal.getAttributes()).thenReturn(Map.of("roles", List.of("ROLE_B")));
        OAuth2AuthenticationToken token = new OAuth2AuthenticationToken(principal, List.of(), "regId");

        service.refreshCurrentSessionAuthorities(token, "ROLE_A", null);
        verifyNoInteractions(authorityMappingService);
    }

    @Test
    void refresh_MatchingRole_OidcUser_ShouldRefresh() {
        Map<String, Object> claims = Map.of(
                "sub", "user1",
                "roles", "ROLE_A",
                "iss", "http://issuer"
        );
        OidcIdToken idToken = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), claims);
        OidcUser oidcUser = new DefaultOidcUser(List.of(), idToken);

        OAuth2AuthenticationToken token = new OAuth2AuthenticationToken(oidcUser, List.of(), "regId");
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);

        BankConfiguration bankConfig = new BankConfiguration();
        bankConfig.setBankId("BANK1");
        bankConfig.setName("Bank One");

        when(authorityMappingService.mapAuthoritiesWithContext(anyMap(), eq("http://issuer")))
                .thenReturn(new AuthorityMappingService.MappingResult(List.of(new SimpleGrantedAuthority("PERM1")), bankConfig));

        service.refreshCurrentSessionAuthorities(token, "ROLE_A", request);

        verify(session).setAttribute(eq(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY), any());
    }

    @Test
    void refresh_OAuth2User_MissingIssuer_ShouldLogWarning() {
        Map<String, Object> claims = Map.of(
                "sub", "user1",
                "roles", List.of("ROLE_A")
        );
        OAuth2User oauth2User = new DefaultOAuth2User(List.of(), claims, "sub");
        OAuth2AuthenticationToken token = new OAuth2AuthenticationToken(oauth2User, List.of(), "regId");

        service.refreshCurrentSessionAuthorities(token, "ROLE_A", null);
        verifyNoInteractions(authorityMappingService);
    }

    @Test
    void refresh_OAuth2User_WithIssuer_ShouldRefresh() {
        Map<String, Object> claims = Map.of(
                "name", "user1",
                "roles", List.of("ROLE_A"),
                "iss", "http://issuer"
        );
        OAuth2User oauth2User = new DefaultOAuth2User(List.of(), claims, "name");
        OAuth2AuthenticationToken token = new OAuth2AuthenticationToken(oauth2User, List.of(), "regId");

        BankConfiguration bankConfig = new BankConfiguration();
        bankConfig.setBankId("BANK1");

        when(authorityMappingService.mapAuthoritiesWithContext(anyMap(), eq("http://issuer")))
                .thenReturn(new AuthorityMappingService.MappingResult(List.of(new SimpleGrantedAuthority("PERM1")), bankConfig));

        service.refreshCurrentSessionAuthorities(token, "ROLE_A", null);
        verify(authorityMappingService).mapAuthoritiesWithContext(anyMap(), eq("http://issuer"));
    }

    @Test
    void refresh_RolesAsList_ShouldRefresh() {
        Map<String, Object> claims = Map.of(
                "sub", "user1",
                "roles", List.of("ROLE_A"),
                "iss", "http://issuer"
        );
        OAuth2User oauth2User = new DefaultOAuth2User(List.of(), claims, "sub");
        OAuth2AuthenticationToken token = new OAuth2AuthenticationToken(oauth2User, List.of(), "regId");
        BankConfiguration bankConfig = new BankConfiguration();
        bankConfig.setBankId("BANK1");

        when(authorityMappingService.mapAuthoritiesWithContext(anyMap(), eq("http://issuer")))
                .thenReturn(new AuthorityMappingService.MappingResult(List.of(new SimpleGrantedAuthority("PERM1")), bankConfig));

        service.refreshCurrentSessionAuthorities(token, "ROLE_A", null);
        verify(authorityMappingService).mapAuthoritiesWithContext(anyMap(), eq("http://issuer"));
    }

    @Test
    void refresh_RolesEmpty_ShouldReturnEarly() {
        Map<String, Object> claims = Map.of(
                "sub", "user1",
                "iss", "http://issuer"
        );
        OAuth2User oauth2User = new DefaultOAuth2User(List.of(), claims, "sub");
        OAuth2AuthenticationToken token = new OAuth2AuthenticationToken(oauth2User, List.of(), "regId");

        service.refreshCurrentSessionAuthorities(token, "ROLE_A", null);
        verifyNoInteractions(authorityMappingService);
    }
}