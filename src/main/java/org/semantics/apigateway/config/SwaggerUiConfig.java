package org.semantics.apigateway.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import org.semantics.apigateway.service.configuration.ConfigurationLoader;
import org.springdoc.core.properties.SwaggerUiConfigParameters;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Makes the {@code database} multi-select of Swagger UI easier to use (see {@link OpenApiConfig}):
 * the list shows every source without scrolling, a plain click selects / deselects a source (no Ctrl/Cmd),
 * and each source is labelled with its host (e.g. "ebi — www.ebi.ac.uk"). Only the displayed text changes:
 * the values sent are still the source names.
 * <p>
 * Done by replacing springdoc's index page transformer (declared {@code @ConditionalOnMissingBean}) with one
 * that also injects a small script into Swagger UI's index.html.
 */
@Configuration
public class SwaggerUiConfig {

    private static final String SCRIPT = """
            <script>
            (function () {
              var labels = %s;
              var SELECTOR = ".swagger-ui tr[data-param-name='database'] select[multiple]";
              function enhance(select) {
                select.size = Math.min(select.options.length, 20);
                for (var i = 0; i < select.options.length; i++) {
                  var o = select.options[i], label = labels[o.value];
                  if (label && o.textContent === o.value) o.textContent = o.value + "  \\u2014  " + label;
                }
              }
              new MutationObserver(function () {
                document.querySelectorAll(SELECTOR).forEach(enhance);
              }).observe(document.documentElement, {childList: true, subtree: true});
              // A plain click toggles one source, instead of replacing the whole selection
              document.addEventListener("mousedown", function (e) {
                var o = e.target, select = o && o.parentElement;
                if (!o || o.tagName !== "OPTION" || !select || !select.matches(SELECTOR)) return;
                e.preventDefault();
                o.selected = !o.selected;
                select.focus();
                select.dispatchEvent(new Event("change", {bubbles: true}));
              }, true);
            })();
            </script>
            """;

    @Bean
    public SwaggerIndexTransformer sourceSelectorIndexTransformer(SwaggerUiConfigProperties swaggerUiConfig,
                                                                  SwaggerUiOAuthProperties swaggerUiOAuthProperties,
                                                                  SwaggerUiConfigParameters swaggerUiConfigParameters,
                                                                  SwaggerWelcomeCommon swaggerWelcomeCommon,
                                                                  ObjectMapperProvider objectMapperProvider,
                                                                  ConfigurationLoader configurationLoader) {
        return new SwaggerIndexPageTransformer(swaggerUiConfig, swaggerUiOAuthProperties, swaggerUiConfigParameters,
                swaggerWelcomeCommon, objectMapperProvider) {
            @Override
            public Resource transform(HttpServletRequest request, Resource resource, ResourceTransformerChain chain)
                    throws IOException {
                if (!"index.html".equals(resource.getFilename())) {
                    return super.transform(request, resource, chain);
                }
                String html = readFullyAsString(resource.getInputStream());
                String script = SCRIPT.formatted(labelsJson(configurationLoader));
                html = html.contains("</body>") ? html.replace("</body>", script + "</body>") : html + script;
                return new TransformedResource(resource, html.getBytes(StandardCharsets.UTF_8));
            }

            private String labelsJson(ConfigurationLoader loader) throws JsonProcessingException {
                Map<String, String> labels = new LinkedHashMap<>();
                var configs = loader.getDatabaseConfigs();
                if (configs != null) {
                    if (configs.stream().anyMatch(DatabaseConfig::isOntoPortal)) {
                        labels.put("ontoportal", "all OntoPortal sources");
                    }
                    configs.forEach(c -> labels.put(c.getName(), hostOf(c.getUrl())));
                }
                // "</" would close the <script> element early
                return objectMapper.writeValueAsString(labels).replace("</", "<\\/");
            }
        };
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null ? host : url;
        } catch (IllegalArgumentException | NullPointerException e) {
            return String.valueOf(url);
        }
    }
}
