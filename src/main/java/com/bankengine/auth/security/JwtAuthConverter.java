package com.bankengine.auth.security;

import com.bankengine.auth.service.AuthorityMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AuthorityMappingService authorityMappingService;

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        String issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : null;
        Collection<GrantedAuthority> authorities = authorityMappingService.mapAuthorities(jwt.getClaims(), issuer);
        return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString("sub"));
    }
}
