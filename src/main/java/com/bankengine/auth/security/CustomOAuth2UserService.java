package com.bankengine.auth.security;

import com.bankengine.auth.service.AuthorityMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final AuthorityMappingService authorityMappingService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);

        // For OAuth2 (non-OIDC), we might not have an issuer in the same way,
        // but we can try to resolve it from the client registration.
        String issuer = userRequest.getClientRegistration().getProviderDetails().getIssuerUri();

        Collection<GrantedAuthority> authorities = authorityMappingService.mapAuthorities(oauth2User.getAttributes(), issuer);

        log.info("[AUTH-SUCCESS] OAuth2 User '{}' authorized with {} permissions via session login",
                oauth2User.getName(), authorities.size());

        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        return new DefaultOAuth2User(authorities, oauth2User.getAttributes(), userNameAttributeName);
    }
}
