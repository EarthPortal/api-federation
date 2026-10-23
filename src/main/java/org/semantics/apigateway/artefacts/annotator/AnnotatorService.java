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
import org.semantics.apigateway.util.OntoPortalUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Service
public class AnnotatorService extends AbstractEndpointService {

    private static final Logger logger = LoggerFactory.getLogger(AnnotatorService.class);
    private final OntoPortalUtil ontoPortalUtil;

    public AnnotatorService(ConfigurationLoader configurationLoader, CacheManager cacheManager, JsonLdTransform jsonLdTransform, ResponseTransformerService responseTransformerService, OntoPortalUtil ontoPortalUtil) {
        super(configurationLoader, cacheManager, jsonLdTransform, responseTransformerService, RDFResource.class);
        this.ontoPortalUtil = ontoPortalUtil;
    }

    public Object performAnnotation(String database, String text, String ontologies, boolean expandMappings, boolean excludeNumbers, boolean excludeSynonyms, User currentUser) {
        String endpoint = "annotator";
        text = removeSpecialChars(text);
        ApiAccessor accessor = initAccessor(database, endpoint, null);
        accessor = applyOntologies(accessor, ontologies);
        accessor = applyFlag(accessor, "expand_mappings", expandMappings);
        accessor = applyFlag(accessor, "exclude_numbers", excludeNumbers);
        accessor = applyFlag(accessor, "exclude_synonyms", excludeSynonyms);
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
            Map<String, String> pullLocations = fetchPullLocations(database);
            List<Object> merged = mergeAnnotations(annotations, pullLocations);
            return buildResponse(merged);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> fetchPullLocations(String database) {
        Map<String, String> pullLocations = new HashMap<>();
        try {
            ApiAccessor accessor = initAccessor(database, "resources", null);
            Map<String, ApiResponse> responses = accessor.get().get();
            for (ApiResponse response : responses.values()) {
                if (response == null || response.getResponseBody() == null) continue;
                Object collection = response.getResponseBody().get("collection");
                if (!(collection instanceof List)) continue;
                for (Object obj : (List<?>) collection) {
                    if (!(obj instanceof Map)) continue;
                    Map<String, Object> submission = (Map<String, Object>) obj;
                    Object ontology = submission.get("ontology");
                    Object pullLocation = submission.get("pullLocation");
                    if (ontology instanceof Map && pullLocation != null) {
                        Object id = ((Map<String, Object>) ontology).get("@id");
                        if (id != null) pullLocations.put(id.toString(), pullLocation.toString());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Could not fetch pullLocations for canonical selection: {}", e.getMessage());
        }
        return pullLocations;
    }

    @SuppressWarnings("unchecked")
    private List<Object> mergeAnnotations(List<Object> annotations, Map<String, String> pullLocations) {
        Set<String> portals = ontoPortalUtil.getOntoPortalPortals();

        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Object obj : annotations) {
            if (!(obj instanceof Map)) continue;
            Map<String, Object> item = (Map<String, Object>) obj;
            String key = buildKey(item);
            if (key == null) continue;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }

        List<Object> result = new ArrayList<>();
        for (List<Map<String, Object>> group : groups.values()) {
            Map<String, Object> canonical = group.size() == 1
                    ? group.get(0)
                    : pickCanonical(group, pullLocations, portals);
            if (group.size() > 1) {
                List<Object> foundIn = new ArrayList<>();
                for (Map<String, Object> item : group) foundIn.add(foundInEntry(item));
                canonical.put("found_in", foundIn);
            }
            result.add(canonical);
        }
        return result;
    }

    private Map<String, Object> pickCanonical(List<Map<String, Object>> group, Map<String, String> pullLocations, Set<String> portals) {
        Map<String, Integer> portalCounts = new HashMap<>();
        for (Map<String, Object> item : group) {
            String pl = pullLocations.get(ontologyUrl(item));
            if (pl == null) continue;
            String plLower = pl.toLowerCase();
            for (String portal : portals) {
                if (plLower.contains(portal)) portalCounts.merge(portal, 1, Integer::sum);
            }
        }
        if (portalCounts.isEmpty()) return group.get(0);

        String winner = portalCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        if (winner != null) {
            for (Map<String, Object> item : group) {
                String url = ontologyUrl(item);
                if (url != null && url.toLowerCase().contains(winner)) return item;
            }
        }
        return group.get(0);
    }

    @SuppressWarnings("unchecked")
    private String ontologyUrl(Map<String, Object> item) {
        Map<String, Object> annotatedClass = (Map<String, Object>) item.get("annotatedClass");
        if (annotatedClass == null) return null;
        Map<String, Object> links = (Map<String, Object>) annotatedClass.get("links");
        return links == null || links.get("ontology") == null ? null : links.get("ontology").toString();
    }

    @SuppressWarnings("unchecked")
    private String buildKey(Map<String, Object> item) {
        Map<String, Object> annotatedClass = (Map<String, Object>) item.get("annotatedClass");
        if (annotatedClass == null) return null;
        Object id = annotatedClass.get("@id");
        String acronym = ontologyAcronym(annotatedClass);
        if (id == null || acronym == null) return null;
        return id + "|" + acronym.toUpperCase();
    }

    @SuppressWarnings("unchecked")
    private String ontologyAcronym(Map<String, Object> annotatedClass) {
        Map<String, Object> links = (Map<String, Object>) annotatedClass.get("links");
        if (links == null || links.get("ontology") == null) return null;
        String ontology = links.get("ontology").toString();
        int idx = ontology.lastIndexOf('/');
        return idx >= 0 ? ontology.substring(idx + 1) : ontology;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> foundInEntry(Map<String, Object> item) {
        Map<String, Object> links = (Map<String, Object>) ((Map<String, Object>) item.get("annotatedClass")).get("links");
        Map<String, Object> entry = new LinkedHashMap<>();
        if (links != null) {
            entry.put("ontology", links.get("ontology"));
            entry.put("ui", links.get("ui"));
        }
        return entry;
    }

    private Map<String, Object> buildResponse(List<Object> collection) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalCount", collection.size());
        response.put("countByPortal", countByPortal(collection));
        response.put("collection", collection);
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> countByPortal(List<Object> collection) {
        Set<String> portals = ontoPortalUtil.getOntoPortalPortals();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Object obj : collection) {
            if (!(obj instanceof Map)) continue;
            Map<String, Object> item = (Map<String, Object>) obj;
            Set<String> itemPortals = new LinkedHashSet<>();
            addPortal(itemPortals, ontologyUrl(item), portals);
            Object foundIn = item.get("found_in");
            if (foundIn instanceof List) {
                for (Object entry : (List<?>) foundIn) {
                    if (entry instanceof Map) {
                        Object ontology = ((Map<String, Object>) entry).get("ontology");
                        if (ontology != null) addPortal(itemPortals, ontology.toString(), portals);
                    }
                }
            }
            for (String portal : itemPortals) counts.merge(portal, 1, Integer::sum);
        }
        return counts;
    }

    private void addPortal(Set<String> accumulator, String url, Set<String> portals) {
        if (url == null) return;
        String lower = url.toLowerCase();
        for (String portal : portals) {
            if (lower.contains(portal)) {
                accumulator.add(portal);
                return;
            }
        }
    }

    private String removeSpecialChars(String text) {
        if (text == null) return null;
        if (text.matches("^[a-zA-Z0-9\\s]*$")) return text;
        return text.replaceAll("[^\\w\\s]", "");
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

    private ApiAccessor applyFlag(ApiAccessor accessor, String name, boolean value) {
        if (!value) {
            return accessor;
        }
        Map<String, UrlConfig> updated = new LinkedHashMap<>();
        accessor.getUrls().forEach((url, cfg) -> {
            String sep = url.contains("?") ? "&" : "?";
            updated.put(url + sep + name + "=true", cfg);
        });
        accessor.setUrls(updated);
        return accessor;
    }
}
