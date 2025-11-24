package com.bankengine.auth.config;

import com.bankengine.auth.security.JwtAuthConverter;
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
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // Inject the secret key from application.properties
    @Value("${security.jwt.secret-key}")
    private String jwtSecretKey;

    // Inject the issuer URI for token validation
    @Value("${security.jwt.issuer-uri}")
    private String jwtIssuerUri;

    private final JwtAuthConverter jwtAuthConverter;

    public SecurityConfig(JwtAuthConverter jwtAuthConverter) {
        this.jwtAuthConverter = jwtAuthConverter;
    }

    // The security filter chain defines authorization rules
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
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
                                "/h2-console/**" // Allow H2 console access in dev
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

    // Bean to configure the JWT Decoder (validation logic)
    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec secretKey = new SecretKeySpec(jwtSecretKey.getBytes(), "HMACSHA256");

        // 1. Create the decoder without claim validation initially
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        // 2. Use JwtValidators helper to create a complete validator (including expiration and issuer)
        OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(jwtIssuerUri);

        // 3. Apply the validator to the decoder
        decoder.setJwtValidator(validator);

        return decoder;
    }
}