package com.bankengine.auth.config;

import com.bankengine.auth.security.JwtAuthConverter;
import com.bankengine.auth.security.TenantContextFilter;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthConverter jwtAuthConverter;
    private final TenantContextFilter tenantContextFilter;

    public SecurityConfig(JwtAuthConverter jwtAuthConverter, TenantContextFilter tenantContextFilter) {
        this.jwtAuthConverter = jwtAuthConverter;
        this.tenantContextFilter = tenantContextFilter;
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
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(jwtAuthConverter)
                ))
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                );

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(OAuth2ResourceServerProperties properties) {
        // 1. Get the issuer exactly as defined in your Environment Variables
        String configuredIssuer = properties.getJwt().getIssuerUri();

        // 2. Initialize the decoder using the OIDC discovery endpoint
        NimbusJwtDecoder jwtDecoder = JwtDecoders.fromIssuerLocation(configuredIssuer);

        OAuth2TokenValidator<Jwt> timestampValidator = new JwtTimestampValidator();

        // 3. DYNAMIC VALIDATOR: Trusts the issuer the app was told to use
        OAuth2TokenValidator<Jwt> issuerValidator = token -> {
            String tokenIssuer = token.getIssuer().toString();

            // Match if token issuer equals configured issuer
            // OR handle the Docker 'identity-provider' vs 'localhost' alias quirk
            if (tokenIssuer.equals(configuredIssuer) ||
                    (configuredIssuer.contains("localhost") && tokenIssuer.contains("identity-provider"))) {
                return OAuth2TokenValidatorResult.success();
            }

            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_issuer",
                    "Token issuer " + tokenIssuer + " does not match configured " + configuredIssuer,
                    null));
        };

        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestampValidator, issuerValidator));
        return jwtDecoder;
    }
}