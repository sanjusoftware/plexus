package com.bankengine.auth.security;

import com.bankengine.auth.service.AuthorityMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final AuthorityMappingService authorityMappingService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String issuer = oidcUser.getIssuer() != null ? oidcUser.getIssuer().toString() : null;
        Collection<GrantedAuthority> authorities = authorityMappingService.mapAuthorities(oidcUser.getAttributes(), issuer);

        log.info("[AUTH-SUCCESS] OIDC User '{}' authorized with {} permissions via session login",
                oidcUser.getName(), authorities.size());

        return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }
}
