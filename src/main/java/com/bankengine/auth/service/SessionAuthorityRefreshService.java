package com.bankengine.auth.service;

import com.bankengine.common.model.BankConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionAuthorityRefreshService {

    private final AuthorityMappingService authorityMappingService;

    public void refreshCurrentSessionAuthorities(Authentication authentication,
                                                 String touchedRoleName,
                                                 HttpServletRequest request) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            return;
        }

        if (!(oauthToken.getPrincipal() instanceof OAuth2User oauth2User)) {
            return;
        }

        List<String> currentRoles = extractRoles(oauth2User.getAttributes());
        if (touchedRoleName != null && currentRoles.stream().noneMatch(r -> r.equalsIgnoreCase(touchedRoleName))) {
            return;
        }

        String issuer = resolveIssuer(oauth2User);
        if (issuer == null || issuer.isBlank()) {
            log.warn("[AUTH] Skipping session authority refresh: issuer missing for user {}", authentication.getName());
            return;
        }

        // Reuse the same role->authority mapping logic used during login.
        AuthorityMappingService.MappingResult mappingResult =
                authorityMappingService.mapAuthoritiesWithContext(oauth2User.getAttributes(), issuer);

        Collection<GrantedAuthority> refreshedAuthorities = mappingResult.authorities();
        BankConfiguration bankConfig = mappingResult.bankConfig();

        Map<String, Object> refreshedClaims = new HashMap<>(oauth2User.getAttributes());
        refreshedClaims.put("bank_id", bankConfig.getBankId());
        refreshedClaims.put("bankName", bankConfig.getName());
        refreshedClaims.put("permissions", refreshedAuthorities.stream().map(GrantedAuthority::getAuthority).toList());

        OAuth2User refreshedPrincipal;
        if (oauth2User instanceof OidcUser oidcUser) {
            refreshedPrincipal = new DefaultOidcUser(
                    refreshedAuthorities,
                    oidcUser.getIdToken(),
                    new OidcUserInfo(refreshedClaims),
                    "sub"
            );
        } else {
            refreshedPrincipal = new DefaultOAuth2User(
                    refreshedAuthorities,
                    refreshedClaims,
                    resolveNameAttributeKey(refreshedClaims)
            );
        }

        OAuth2AuthenticationToken refreshedAuth = new OAuth2AuthenticationToken(
                refreshedPrincipal,
                refreshedAuthorities,
                oauthToken.getAuthorizedClientRegistrationId()
        );
        refreshedAuth.setDetails(oauthToken.getDetails());

        org.springframework.security.core.context.SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(refreshedAuth);
        SecurityContextHolder.setContext(context);

        HttpSession session = request != null ? request.getSession(false) : null;
        if (session != null) {
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        }

        log.info("[AUTH] Refreshed session authorities for user '{}' after role change '{}'.", authentication.getName(), touchedRoleName);
    }

    private String resolveIssuer(OAuth2User oauth2User) {
        if (oauth2User instanceof OidcUser oidcUser && oidcUser.getIssuer() != null) {
            return oidcUser.getIssuer().toString();
        }

        Object issuer = oauth2User.getAttributes().get("iss");
        return issuer != null ? issuer.toString() : null;
    }

    private List<String> extractRoles(Map<String, Object> claims) {
        Object roles = claims.get("roles");
        if (roles instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        if (roles instanceof String role) {
            return List.of(role);
        }
        return Collections.emptyList();
    }

    private String resolveNameAttributeKey(Map<String, Object> claims) {
        if (claims.containsKey("sub")) {
            return "sub";
        }
        if (claims.containsKey("name")) {
            return "name";
        }
        return claims.keySet().stream().findFirst().orElse("sub");
    }
}

