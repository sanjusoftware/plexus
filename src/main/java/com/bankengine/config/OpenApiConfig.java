package com.bankengine.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Product Catalog & Pricing Engine",
        description = "Comprehensive management of banking products, features, and dynamic pricing rules.",
        version = "v1.0.0",
        contact = @Contact(
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
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "Enter JWT Bearer token **without** the 'Bearer ' prefix."
)
public class OpenApiConfig {
    // This class remains empty, configuration is done via annotations.
}