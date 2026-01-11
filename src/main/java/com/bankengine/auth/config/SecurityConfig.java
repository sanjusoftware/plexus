package com.bankengine.auth.config;

import com.bankengine.auth.security.JwtAuthConverter;
import com.bankengine.auth.security.TenantContextFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${security.jwt.secret-key}")
    private String jwtSecretKey;

    @Value("${security.jwt.issuer-uri}")
    private String jwtIssuerUri;

    private final JwtAuthConverter jwtAuthConverter;
    private final TenantContextFilter tenantContextFilter; // <-- NEW FIELD

    public SecurityConfig(JwtAuthConverter jwtAuthConverter, TenantContextFilter tenantContextFilter) { // <-- INJECT FILTER
        this.jwtAuthConverter = jwtAuthConverter;
        this.tenantContextFilter = tenantContextFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 5. TENANCY FILTER: Inject the TenantContextFilter AFTER the JWT (Bearer Token) authentication has occurred.
                .addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter.class)

                // 1. STATELESS: Use stateless session management (essential for JWT)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 2. CSRF: Disable CSRF since sessions are stateless
                .csrf(AbstractHttpConfigurer::disable)

                // 3. AUTHORIZATION: Define access rules
                .authorizeHttpRequests(authorize -> authorize
                        // Allow OpenAPI (Swagger) documentation access without authentication
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/h2-console/**",
                                "/error"
                        ).permitAll()

                        // Allow POST for /products for initial testing
                        .requestMatchers(HttpMethod.POST, "/api/v1/products").authenticated()

                        // All other API requests must be authenticated
                        .requestMatchers("/api/v1/**").authenticated()

                        // Deny all other requests by default
                        .anyRequest().denyAll()
                )

                // 4. RESOURCE SERVER: Configure OAuth2 to process JWT Bearer tokens
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder())
                        .jwtAuthenticationConverter(jwtAuthConverter)
                ))

                // This is required for H2 console to load inside a frame
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                );

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec secretKey = new SecretKeySpec(jwtSecretKey.getBytes(), "HMACSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(jwtIssuerUri);
        decoder.setJwtValidator(validator);

        return decoder;
    }
}