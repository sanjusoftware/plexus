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
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final AuthorityMappingService authorityMappingService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);
        return enrichUser(userRequest, oauth2User);
    }

    /**
     * Extracted logic for testing without triggering network calls via super.loadUser()
     */
    protected OAuth2User enrichUser(OAuth2UserRequest userRequest, OAuth2User oauth2User) {
        // Resolve issuer from client registration details
        String issuer = userRequest.getClientRegistration().getProviderDetails().getIssuerUri();

        // 1. Use the unified mapping method
        AuthorityMappingService.MappingResult mappingResult =
                authorityMappingService.mapAuthoritiesWithContext(oauth2User.getAttributes(), issuer);

        Collection<GrantedAuthority> authorities = mappingResult.authorities();
        var bankConfig = mappingResult.bankConfig();

        log.info("[AUTH-SUCCESS] OAuth2 User '{}' authorized for bank '{}' with {} permissions",
                oauth2User.getName(), bankConfig.getBankId(), authorities.size());

        // 2. Enrich attributes for the session
        Map<String, Object> enrichedAttributes = new HashMap<>(oauth2User.getAttributes());
        enrichedAttributes.put("bank_id", bankConfig.getBankId());
        enrichedAttributes.put("bankName", bankConfig.getName());
        enrichedAttributes.put("permissions", authorities.stream().map(GrantedAuthority::getAuthority).toList());

        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        if (userNameAttributeName == null) userNameAttributeName = "sub";

        return new DefaultOAuth2User(authorities, enrichedAttributes, userNameAttributeName);
    }
}