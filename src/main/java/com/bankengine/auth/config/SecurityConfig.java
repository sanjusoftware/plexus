package com.bankengine.auth.config;

import com.bankengine.auth.security.*;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

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
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final DynamicClientRegistrationRepository clientRegistrationRepository;

    public SecurityConfig(JwtAuthConverter jwtAuthConverter,
                          TenantContextFilter tenantContextFilter,
                          BankConfigurationRepository bankConfigurationRepository,
                          CustomAuthenticationEntryPoint authenticationEntryPoint,
                          CustomAccessDeniedHandler accessDeniedHandler,
                          DynamicClientRegistrationRepository clientRegistrationRepository) {
        this.jwtAuthConverter = jwtAuthConverter;
        this.tenantContextFilter = tenantContextFilter;
        this.bankConfigurationRepository = bankConfigurationRepository;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Value("${app.security.csrf.enabled:true}")
    private boolean csrfEnabled;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

        // Configure Authorization Request Resolver to keep bankId in session for callback matching
        // Alternatively, use a custom AuthorizationCodeTokenResponseClient or similar if needed.
        // For now, we will use a common callback and ensure registration matching.

        if (csrfEnabled) {
            http.csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .ignoringRequestMatchers(
                            "/api/v1/public/catalog/**",
                            "/api/v1/public/onboarding/**",
                            "/api/v1/auth/login",
                            "/h2-console/**",
                            "/api/v1/pricing/calculate**"
                    )
            );
        } else {
            http.csrf(csrf -> csrf.disable());
        }

        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/static/**",
                                "/*.png",
                                "/*.json",
                                "/*.ico",
                                "/login",
                                "/dashboard",
                                "/auth/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/h2-console/**",
                                "/error",
                                "/actuator/health",
                                "/api/v1/public/catalog/**",
                                "/api/v1/public/onboarding/**",
                                "/api/v1/auth/login"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/products").authenticated()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .clientRegistrationRepository(clientRegistrationRepository)
                        .loginProcessingUrl("/login/oauth2/code/callback")
                        .defaultSuccessUrl("/dashboard", true)
                        .failureUrl("/login?error=auth_failed")
                )
                // Use the Dynamic Resolver instead of a static JWT Decoder
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationManagerResolver(tenantAuthenticationManagerResolver())
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
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
            } catch (ParseException | IllegalArgumentException e) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("invalid_token", "Invalid JWT format", null)
                );
            }
        }
        throw new OAuth2AuthenticationException(
                new OAuth2Error("missing_token", "Missing Bearer token", null)
        );
    }
}
