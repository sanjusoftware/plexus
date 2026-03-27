package com.bankengine.auth.config;

import com.bankengine.auth.security.*;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

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
    private final CustomOidcUserService customOidcUserService;
    private final CustomOAuth2UserService customOAuth2UserService;

    public SecurityConfig(JwtAuthConverter jwtAuthConverter,
                          TenantContextFilter tenantContextFilter,
                          BankConfigurationRepository bankConfigurationRepository,
                          CustomAuthenticationEntryPoint authenticationEntryPoint,
                          CustomAccessDeniedHandler accessDeniedHandler,
                          DynamicClientRegistrationRepository clientRegistrationRepository,
                          CustomOidcUserService customOidcUserService,
                          CustomOAuth2UserService customOAuth2UserService) {
        this.jwtAuthConverter = jwtAuthConverter;
        this.tenantContextFilter = tenantContextFilter;
        this.bankConfigurationRepository = bankConfigurationRepository;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.customOidcUserService = customOidcUserService;
        this.customOAuth2UserService = customOAuth2UserService;
    }

    @Value("${app.security.csrf.enabled:true}")
    private boolean csrfEnabled;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .addFilterAfter(tenantContextFilter, SecurityContextHolderFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

        if (csrfEnabled) {
            http.csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .ignoringRequestMatchers(
                            "/api/v1/public/catalog/**",
                            "/api/v1/public/onboarding/**",
                            "/api/v1/auth/login",
                            "/logout",
                            "/h2-console/**",
                            "/api/v1/pricing/calculate/**"
                    )
            );
        } else {
            http.csrf(csrf -> csrf.disable());
        }

        http
                .exceptionHandling(exceptions -> exceptions
                        // For API requests, return 401 instead of redirecting to login page
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new AntPathRequestMatcher("/api/**")
                        )
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/static/**",
                                "/*.png",
                                "/*.json",
                                "/*.ico",
                                "/login",
                                "/api/v1/auth/check-bank",
                                "/api/v1/auth/csrf",
                                "/dashboard",
                                "/product-types",
                                "/product-types/**",
                                "/pricing-metadata",
                                "/pricing-metadata/**",
                                "/pricing-components/**",
                                "/products/**",
                                "/roles/**",
                                "/auth/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/h2-console/**",
                                "/error",
                                "/onboarding",
                                "/dashboard",
                                "/*.svg",
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
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(customOidcUserService)
                                .userService(customOAuth2UserService)
                        )
                        .loginProcessingUrl("/login/oauth2/code/callback")
                        .defaultSuccessUrl("/dashboard", true)
                        .failureUrl("/login?error=auth_failed")
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationManagerResolver(tenantAuthenticationManagerResolver())
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        // Return 200 OK for AJAX logouts so React can handle the redirect
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(HttpServletResponse.SC_OK);
                        })
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID", "SESSION", "XSRF-TOKEN")
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
                if (!bankConfigurationRepository.existsByIssuerUrl(issuer)) {
                    log.warn("Access denied: No bank found with Issuer {} in system.", issuer);
                    throw new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "Untrusted issuer: " + issuer);
                }
            } finally {
                TenantContextHolder.setSystemMode(false);
            }

            var provider = new JwtAuthenticationProvider(JwtDecoders.fromIssuerLocation(issuer));
            provider.setJwtAuthenticationConverter(jwtAuthConverter);
            return provider::authenticate;
        };
    }

    private String resolveIssuer(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            try {
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