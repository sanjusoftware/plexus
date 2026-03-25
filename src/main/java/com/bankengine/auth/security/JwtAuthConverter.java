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

        // 1. Use the new mapping method to ensure Bank Status and Issuer Normalization are applied
        AuthorityMappingService.MappingResult mappingResult =
                authorityMappingService.mapAuthoritiesWithContext(jwt.getClaims(), issuer);

        Collection<GrantedAuthority> authorities = mappingResult.authorities();

        // Note: For stateless JWT Bearer tokens, we don't usually store the bankConfig
        // in a session, but we can log it here for audit purposes.
        log.debug("[JWT-AUTH] Authenticated user '{}' for tenant '{}'",
                jwt.getClaimAsString("sub"), mappingResult.bankConfig().getBankId());

        return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString("sub"));
    }
}