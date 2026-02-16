package com.bankengine.auth.config;

import com.bankengine.auth.security.CustomAuthenticationEntryPoint;
import com.bankengine.auth.security.JwtAuthConverter;
import com.bankengine.auth.security.TenantContextFilter;
import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.text.ParseException;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Slf4j
public class SecurityConfig {

    private final JwtAuthConverter jwtAuthConverter;
    private final TenantContextFilter tenantContextFilter;
    private final BankConfigurationRepository bankConfigurationRepository;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;

    public SecurityConfig(JwtAuthConverter jwtAuthConverter,
                          TenantContextFilter tenantContextFilter,
                          BankConfigurationRepository bankConfigurationRepository, CustomAuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtAuthConverter = jwtAuthConverter;
        this.tenantContextFilter = tenantContextFilter;
        this.bankConfigurationRepository = bankConfigurationRepository;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/h2-console/**",
                                "/error",
                                "/actuator/health",
                                "/api/v1/public/catalog/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/products").authenticated()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll()
                )
                // Use the Dynamic Resolver instead of a static JWT Decoder
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationManagerResolver(tenantAuthenticationManagerResolver())
                        .authenticationEntryPoint(authenticationEntryPoint)
                )
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                );

        return http.build();
    }

    @Bean
    public AuthenticationManagerResolver<HttpServletRequest> tenantAuthenticationManagerResolver() {
        return request -> {
            String issuer = resolveIssuer(request);

            try {
                TenantContextHolder.setSystemMode(true);
                // SECURITY REQUIREMENT: Only trust issuers stored in our DB during onboarding
                if (!bankConfigurationRepository.existsByIssuerUrl(issuer)) {
                    log.warn("Access denied: No bank found with Issuer {} in system.", issuer);
                    throw new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "Untrusted issuer: " + issuer);
                }
            } finally {
                TenantContextHolder.setSystemMode(false);
            }

            // Dynamically create a provider that fetches keys from the specific tenant's OIDC metadata
            var provider = new JwtAuthenticationProvider(JwtDecoders.fromIssuerLocation(issuer));
            provider.setJwtAuthenticationConverter(jwtAuthConverter);
            return provider::authenticate;
        };
    }

    private String resolveIssuer(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            try {
                // Parse the JWT without validating signature yet to find who issued it
                return SignedJWT.parse(token.substring(7)).getJWTClaimsSet().getIssuer();
            } catch (ParseException e) {
                throw new IllegalArgumentException("Invalid JWT format");
            }
        }
        throw new IllegalArgumentException("Missing Bearer token");
    }
}