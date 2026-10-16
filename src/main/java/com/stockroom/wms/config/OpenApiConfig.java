package com.stockroom.wms.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI stockroomOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Stockroom WMS - Warehouse Management API")
                        .description("High-throughput warehouse management system for Kenyan FMCG distributors & logistics operators. " +
                                "Supports multi-depot stock tracking, reservations with optimistic locking concurrency control, " +
                                "immutable stock ledger reconciliation, GS1-128 barcode simulation, and enterprise PDF/Excel reporting.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Stockroom Logistics Engineering")
                                .email("engineering@stockroom.co.ke")
                                .url("https://stockroom.co.ke"))
                        .license(new License().name("Apache 2.0").url("https://springdoc.org")))
                .servers(List.of(
                        new Server().url("/api/v1").description("Default API Gateway Context")
                ))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
