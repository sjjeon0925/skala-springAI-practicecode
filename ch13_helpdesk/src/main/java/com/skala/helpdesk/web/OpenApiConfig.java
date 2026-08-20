package com.skala.helpdesk.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** Swagger UI 우측 상단 "Authorize" 버튼에 HTTP Basic 로그인을 노출한다. */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME = "basicAuth";

    @Bean
    public OpenAPI helpDeskOpenApi() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME));
    }
}
