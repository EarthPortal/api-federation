package org.semantics.apigateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.tags.Tag;
import org.semantics.apigateway.artefacts.annotator.AnnotatorController;
import org.semantics.apigateway.service.configuration.ConfigurationLoader;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines the OpenAPI metadata programmatically so the same image can expose a different
 * title / description / source list per instance (e.g. a full "gateway" vs an OntoPortal-only
 * "federation" instance), driven by configuration instead of hardcoded annotations.
 */
@Configuration
public class OpenApiConfig {

    private final ConfigurationLoader configurationLoader;

    @Value("${gateway.openapi.title:}")
    private String openapiTitle;

    @Value("${gateway.openapi.description:}")
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
     * Turns the {@code database} parameter into a multi-select list of the sources this instance actually
     * loaded ({@link ConfigurationLoader#getDatabaseConfigs()}), plus {@code ontoportal} for all OntoPortal
     * sources at once. Serialized as {@code style=form, explode=false}, so Swagger UI sends the selection
     * comma-separated ({@code database=earthportal,agroportal}), which is what the controllers expect.
     * The annotator only exists on OntoPortal sources, so its list is restricted to them.
     * The hardcoded {@code @Parameter} descriptions in the controllers act as a static fallback.
     */
    @Bean
    public OperationCustomizer databaseParamCustomizer() {
        return (operation, handlerMethod) -> {
            var configs = configurationLoader.getDatabaseConfigs();
            if (configs == null || configs.isEmpty() || operation.getParameters() == null) {
                return operation;
            }
            boolean ontoPortalOnly = AnnotatorController.class.equals(handlerMethod.getBeanType());
            List<String> sources = configs.stream()
                    .filter(c -> !ontoPortalOnly || c.isOntoPortal())
                    .map(DatabaseConfig::getName)
                    .toList();
            boolean hasOntoportal = configs.stream().anyMatch(DatabaseConfig::isOntoPortal);

            List<String> values = new ArrayList<>();
            if (hasOntoportal) values.add(ONTOPORTAL_GROUP);
            values.addAll(sources);

            String description = "Which source(s) to query. Click a source to select or deselect it"
                    + (hasOntoportal ? "; '" + ONTOPORTAL_GROUP + "' queries all OntoPortal sources at once" : "")
                    + ". In a URL, separate several sources with commas, e.g. database=earthportal,agroportal.";

            operation.getParameters().stream()
                    .filter(p -> "database".equals(p.getName()))
                    .forEach(p -> toSourceList(p, values, description));
            return operation;
        };
    }

    private static final String ONTOPORTAL_GROUP = "ontoportal";

    private static void toSourceList(Parameter parameter, List<String> values, String description) {
        // Preselect the controller's example (e.g. "ontoportal") when it is one of the values
        Object example = parameter.getExample();
        ArraySchema schema = new ArraySchema().items(new StringSchema()._enum(values));
        if (example != null && values.contains(example.toString())) {
            schema.setDefault(List.of(example.toString()));
        }
        parameter.setSchema(schema);
        parameter.setStyle(Parameter.StyleEnum.FORM);
        parameter.setExplode(false);
        parameter.setExample(null);
        parameter.setDescription(description);
    }
}
