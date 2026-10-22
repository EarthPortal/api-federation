package org.semantics.apigateway.artefacts.annotator;

import org.semantics.apigateway.model.RDFResource;
import org.semantics.apigateway.model.responses.ApiResponse;
import org.semantics.apigateway.model.user.User;
import org.semantics.apigateway.service.AbstractEndpointService;
import org.semantics.apigateway.service.ApiAccessor;
import org.semantics.apigateway.service.JsonLdTransform;
import org.semantics.apigateway.service.ResponseTransformerService;
import org.semantics.apigateway.service.UrlConfig;
import org.semantics.apigateway.service.configuration.ConfigurationLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Service
public class AnnotatorService extends AbstractEndpointService {

    private static final Logger logger = LoggerFactory.getLogger(AnnotatorService.class);

    public AnnotatorService(ConfigurationLoader configurationLoader, CacheManager cacheManager, JsonLdTransform jsonLdTransform, ResponseTransformerService responseTransformerService) {
        super(configurationLoader, cacheManager, jsonLdTransform, responseTransformerService, RDFResource.class);
    }

    public Object performAnnotation(String database, String text, String ontologies, User currentUser) {
        String endpoint = "annotator";
        ApiAccessor accessor = initAccessor(database, endpoint, null);
        accessor = applyOntologies(accessor, ontologies);
        try {
            Map<String, ApiResponse> responses = accessor.get(text).get();
            List<Object> annotations = new ArrayList<>();
            for (ApiResponse response : responses.values()) {
                if (response == null || response.getResponseBody() == null) continue;
                Object collection = response.getResponseBody().get("collection");
                if (collection instanceof List) {
                    annotations.addAll((List<?>) collection);
                }
            }
            return annotations;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    private ApiAccessor applyOntologies(ApiAccessor accessor, String ontologies) {
        if (ontologies == null || ontologies.isEmpty()) {
            return accessor;
        }
        Map<String, UrlConfig> updated = new LinkedHashMap<>();
        accessor.getUrls().forEach((url, cfg) -> {
            String sep = url.contains("?") ? "&" : "?";
            updated.put(url + sep + "ontologies=" + ontologies, cfg);
        });
        accessor.setUrls(updated);
        return accessor;
    }
}
