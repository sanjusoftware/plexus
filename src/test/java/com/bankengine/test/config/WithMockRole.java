package com.bankengine.test.config;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.auth.service.PermissionMappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.support.WithSecurityContext;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockRole.SecurityContextFactory.class)
public @interface WithMockRole {

    String[] roles() default {};
    String bankId() default "BANK_A";
    String issuer() default "https://login.microsoftonline.com/common/v2.0";

    class SecurityContextFactory implements WithSecurityContextFactory<WithMockRole> {
        @Autowired
        private PermissionMappingService permissionMappingService;

        @Override
        public SecurityContext createSecurityContext(WithMockRole annotation) {
            TenantContextHolder.setBankId(annotation.bankId());
            SecurityContext context = SecurityContextHolder.createEmptyContext();

            Set<String> authorityNames = permissionMappingService.getPermissionsForRoles(
                    Arrays.asList(annotation.roles()));

            Set<GrantedAuthority> authorities = authorityNames.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toSet());

            Jwt jwt = Jwt.withTokenValue("mock-token")
                    .header("alg", "none")
                    .claim("sub", "testUser")
                    .claim("bank_id", annotation.bankId())
                    .claim("roles", Arrays.asList(annotation.roles()))
                    .issuer(annotation.issuer())
                    .build();

            JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, authorities, "testUser");
            context.setAuthentication(auth);
            return context;
        }
    }
}