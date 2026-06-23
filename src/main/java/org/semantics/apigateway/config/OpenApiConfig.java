package org.semantics.apigateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import org.semantics.apigateway.service.configuration.ConfigurationLoader;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Defines the OpenAPI metadata programmatically so the same image can expose a different
 * title / description / source list per instance (e.g. a full "gateway" vs an OntoPortal-only
 * "federation" instance), driven by configuration instead of hardcoded annotations.
 */
@Configuration
public class OpenApiConfig {

    private final ConfigurationLoader configurationLoader;

    @Value("${gateway.openapi.title:API Federation Documentation}")
    private String openapiTitle;

    @Value("${gateway.openapi.description:The EarthPortal API Federated Service is an advanced, dynamic solution designed to perform federated calls across multiple Terminology Services (TS). It offers search capabilities and supports responses in both JSON and JSON-LD formats.}")
    private String openapiDescription;

    public OpenApiConfig(ConfigurationLoader configurationLoader) {
        this.configurationLoader = configurationLoader;
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(openapiTitle)
                        .version("1.0")
                        .description(openapiDescription))
                .tags(List.of(
                        new Tag().name("Search").description("The search endpoints"),
                        new Tag().name("Artefacts / Metadata").description("The artefacts metadata endpoints")
                ));
    }

    /**
     * Rewrites the {@code database} parameter description at doc-generation time so each instance
     * advertises only the sources it actually loaded ({@link ConfigurationLoader#getDatabaseConfigs()}).
     * The hardcoded {@code @Parameter} descriptions in the controllers act as a static fallback.
     */
    @Bean
    public OperationCustomizer databaseParamCustomizer() {
        return (operation, handlerMethod) -> {
            var configs = configurationLoader.getDatabaseConfigs();
            if (configs == null || configs.isEmpty() || operation.getParameters() == null) {
                return operation;
            }
            String sources = configs.stream()
                    .map(c -> c.getName())
                    .collect(Collectors.joining(", "));
            boolean hasOntoportal = configs.stream()
                    .anyMatch(c -> "ontoportal".equalsIgnoreCase(c.getType()));
            String description = "Which source(s) to query. Pass one source, several comma-separated "
                    + "(e.g. earthportal,agroportal), "
                    + (hasOntoportal ? "or 'ontoportal' to query all OntoPortal sources at once. " : "")
                    + "Available sources: " + sources + ".";
            operation.getParameters().stream()
                    .filter(p -> "database".equals(p.getName()))
                    .forEach(p -> p.setDescription(description));
            return operation;
        };
    }
}
