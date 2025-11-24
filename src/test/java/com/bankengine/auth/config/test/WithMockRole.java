package com.bankengine.auth.config.test;

import com.bankengine.auth.service.PermissionMappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContext;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockRole.SecurityContextFactory.class)
public @interface WithMockRole {

    String[] roles() default {}; // Input: The roles the user possesses (e.g., "ADMIN")

    class SecurityContextFactory implements WithSecurityContextFactory<WithMockRole> {

        // This bean is automatically injected by Spring's test framework
        @Autowired
        private PermissionMappingService permissionMappingService;

        @Override
        public SecurityContext createSecurityContext(WithMockRole annotation) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();

            // 1. Get the authorities (permissions) based on the roles via the service
            List<String> roleNames = Arrays.asList(annotation.roles());

            // This is the CRITICAL step: fetching permissions via the service (mimicking JwtAuthConverter)
            Set<String> authorityNames = permissionMappingService.getPermissionsForRoles(roleNames);

            // 2. Convert permissions to SimpleGrantedAuthority objects
            Set<GrantedAuthority> authorities = authorityNames.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toSet());

            // 3. Create the Authentication Token
            // The principal is typically the username or user ID (sub)
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    "testUser",
                    "N/A",
                    authorities
            );

            context.setAuthentication(auth);
            return context;
        }
    }
}