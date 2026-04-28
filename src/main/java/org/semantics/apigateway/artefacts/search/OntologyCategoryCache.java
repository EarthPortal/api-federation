package org.semantics.apigateway.artefacts.search;

import org.semantics.apigateway.config.DatabaseConfig;
import org.semantics.apigateway.service.configuration.ConfigurationLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OntologyCategoryCache {

    private static final Logger logger = LoggerFactory.getLogger(OntologyCategoryCache.class);

    private final ConfigurationLoader configurationLoader;
    private final RestTemplate restTemplate = new RestTemplate();

    private final Map<String, Map<String, List<String>>> cache = new ConcurrentHashMap<>();

    public OntologyCategoryCache(ConfigurationLoader configurationLoader) {
        this.configurationLoader = configurationLoader;
    }

    public List<String> getCategories(String portal, String acronym) {
        if (portal == null || acronym == null) return Collections.emptyList();
        Map<String, List<String>> portalMap = cache.computeIfAbsent(portal.toLowerCase(), this::loadPortal);
        return portalMap.getOrDefault(acronym.toUpperCase(), Collections.emptyList());
    }

    private Map<String, List<String>> loadPortal(String portal) {
        Map<String, List<String>> result = new HashMap<>();
        try {
            DatabaseConfig config = configurationLoader.getConfigByName(portal);
            if (config == null || !config.isOntoPortal()) return result;

            String url = config.getUrl() + "/ontologies?display=acronym,hasDomain&apikey=" + config.getApiKey().trim();
            logger.info("Loading categories cache for portal {} from {}", portal, url);

            List<Map<String, Object>> ontologies = restTemplate.getForObject(url, List.class);
            if (ontologies == null) return result;

            for (Map<String, Object> ontology : ontologies) {
                Object acronymObj = ontology.get("acronym");
                if (acronymObj == null) continue;
                String acronym = acronymObj.toString().toUpperCase();

                Object hasDomain = ontology.get("hasDomain");
                if (!(hasDomain instanceof List)) continue;

                List<String> categories = new ArrayList<>();
                for (Object categoryUri : (List<?>) hasDomain) {
                    if (categoryUri == null) continue;
                    categories.add(extractAcronym(categoryUri.toString()));
                }
                if (!categories.isEmpty()) {
                    result.put(acronym, categories);
                }
            }
            logger.info("Loaded {} ontology-category mappings for {}", result.size(), portal);
        } catch (Exception e) {
            logger.error("Failed to load categories for portal {}: {}", portal, e.getMessage());
        }
        return result;
    }

    private String extractAcronym(String ontologyUri) {
        int idx = ontologyUri.lastIndexOf('/');
        String acronym = idx >= 0 ? ontologyUri.substring(idx + 1) : ontologyUri;
        return acronym.toUpperCase();
    }
}
