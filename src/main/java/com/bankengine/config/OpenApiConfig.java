package com.bankengine.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.*;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Product Catalog & Pricing Engine",
        description = "Comprehensive management of banking products, features, and dynamic pricing rules.",
        version = "v1.0.0",
        contact = @Contact(
            name = "Sanjeev Mishra",
            email = "sanjusoftware@gmail.com"
        ),
        license = @License(
            name = "Proprietary - All Rights Reserved",
            url = "https://yourcompanywebsite.com/licenses/proprietary"
        )
    ),
    security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.OAUTH2, // Using OAUTH2 to support both "Paste Token" and "Login Button"
    description = "You can manually paste a JWT token or use the OAuth2 login flow.",
    flows = @OAuthFlows(
        authorizationCode = @OAuthFlow(
            authorizationUrl = "${swagger.auth-url}",
            tokenUrl = "${swagger.token-url}",
            scopes = {
                @OAuthScope(name = "openid", description = "Standard OIDC"),
                @OAuthScope(name = "profile", description = "User profile")
            }
        )
    )
)
public class OpenApiConfig {
}