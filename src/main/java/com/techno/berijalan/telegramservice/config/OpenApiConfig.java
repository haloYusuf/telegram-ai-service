package com.techno.berijalan.telegramservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @org.springframework.beans.factory.annotation.Value("${security.api-key}")
    private String apiKey;

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "ApiKeyAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Telegram Omnichannel & Multimodal Service API")
                        .version("1.0.0")
                        .description("Dokumentasi pengelolaan Telegram Bot, inbound webhook, dan multimodal omnichannel messaging.")
                        .contact(new Contact().name("Technocenter Batch 18")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name("X-API-KEY")
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .description("Kunci otentikasi API: " + apiKey)));
    }
}

