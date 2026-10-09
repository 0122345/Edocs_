package com.edocs.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

// Interactive API docs at /api/docs.
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI edocsOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Edocs API").version("0.1.0").description("E-contract lifecycle, e-signature and WORM archive API."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
