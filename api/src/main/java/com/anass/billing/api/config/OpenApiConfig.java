package com.anass.billing.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI billingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Billing & Payment Orchestration API")
                        .version("v1")
                        .description("Charges, provider webhooks, and admin operations. "
                                + "Log in at /auth/login, then use Authorize to paste the access token."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                // every operation requires the token unless it opts out with @SecurityRequirements
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}