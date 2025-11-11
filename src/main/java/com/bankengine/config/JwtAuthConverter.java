package com.bankengine.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String AUTHORITIES_CLAIM_NAME = "scope";

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        return new JwtAuthenticationToken(jwt, authorities, getPrincipalClaimName(jwt));
    }

    private String getPrincipalClaimName(Jwt jwt) {
        // "sub" is the standard claim for subject (user identifier)
        return jwt.getClaimAsString("sub");
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        // 1. Check for the actual claim: "scope"
        if (jwt.getClaim(AUTHORITIES_CLAIM_NAME) == null) {
            return Collections.emptyList();
        }

        // 2. Read the claim using the correct name: "scope"
        // Note: We are assuming the claim is a List<String>.
        List<String> permissions = jwt.getClaimAsStringList(AUTHORITIES_CLAIM_NAME);

        // 3. Map the strings to GrantedAuthority objects
        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}