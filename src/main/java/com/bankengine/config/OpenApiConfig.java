package com.bankengine.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Value("${swagger.auth-url}")
    private String authUrl;

    @Value("${swagger.token-url}")
    private String tokenUrl;

    @Value("${swagger.api-scope}")
    private String apiScope;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Product Catalog & Pricing Engine")
                        .description("Comprehensive management of banking products, features, and dynamic pricing rules.")
                        .version("v1.0.0")
                        .contact(new Contact().name("Sanjeev Mishra").email("sanjusoftware@gmail.com"))
                        .license(new License().name("Proprietary").url("https://bankengine.ai/licenses/proprietary")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .description("OAuth2 flow for Entra ID or Mock Server.")
                                .flows(new OAuthFlows()
                                        .authorizationCode(new OAuthFlow()
                                                .authorizationUrl(authUrl)
                                                .tokenUrl(tokenUrl)
                                                .scopes(new Scopes()
                                                        .addString("openid", "Standard OIDC")
                                                        .addString("profile", "User profile info")
                                                        .addString("offline_access", "Refresh tokens")
                                                        .addString(apiScope, "API Access Scope")
                                                )))));
    }
}